package ch.sventschui.nexus.casc;

import ch.sventschui.nexus.casc.config.*;
import org.eclipse.sisu.Description;
import org.sonatype.nexus.CoreApi;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.capability.*;
import org.sonatype.nexus.cleanup.storage.CleanupPolicy;
import org.sonatype.nexus.cleanup.storage.CleanupPolicyStorage;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.security.SecurityApi;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.realm.RealmManager;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Named("cascPlugin")
@Description("Casc Plugin")
// Plugin must run after CAPABILITIES phase as otherwise we can not load/patch existing capabilities
@ManagedLifecycle(phase = ManagedLifecycle.Phase.TASKS)
@Singleton
public class NexusCascPlugin extends StateGuardLifecycleSupport {
    private final CoreApi coreApi;
    private final SecurityApi securityApi;
    private final SecuritySystem securitySystem;
    private final CleanupPolicyStorage cleanupPolicyStorage;
    private final Interpolator interpolator;
    private final RepositoryManager repositoryManager;
    private final BlobStoreManager blobStoreManager;
    private final RealmManager realmManager;
    private final CapabilityRegistry capabilityRegistry;

    @Inject
    public NexusCascPlugin(
            final CoreApi coreApi,
            final SecurityApi securityApi,
            final CleanupPolicyStorage cleanupPolicyStorage,
            final Interpolator interpolator,
            final RepositoryManager repositoryManager,
            final BlobStoreManager blobStoreManager,
            final RealmManager realmManager,
            final CapabilityRegistry capabilityRegistry) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        this.coreApi = coreApi;
        this.securityApi = securityApi;
        this.securitySystem = resolveSecuritySystem(securityApi);
        this.blobStoreManager = blobStoreManager;
        this.cleanupPolicyStorage = cleanupPolicyStorage;
        this.interpolator = interpolator;
        this.repositoryManager = repositoryManager;
        this.realmManager = realmManager;
        this.capabilityRegistry = capabilityRegistry;
    }

    @Override
    protected void doStart() throws Exception {
        String configFile = System.getenv("NEXUS_CASC_CONFIG");

        if (configFile == null) {
            log.error("Env var NEXUS_CASC_CONFIG not found");
            return;
        }

        Config config;
        Yaml yaml = new Yaml(new Constructor(Config.class));
        try {
            String yml = interpolator.interpolate(new String(Files.readAllBytes(Paths.get(configFile))));
            config = yaml.load(yml);
        } catch (IOException e) {
            log.error("Failed to load config file from {}", configFile, e);
            return;
        }

        ConfigCore core = config.getCore();
        if (core != null) {
            applyBaseUrlConfig(core);
            applyProxyConfig(core);
        }

        ConfigSecurity security = config.getSecurity();
        if (security != null) {
            applySecurityConfig(security);
        }

        ConfigRepository repository = config.getRepository();
        if (repository != null) {
            applyRepositoryConfig(repository);
        }

        List<ConfigCapability> capabilities = config.getCapabilities();
        if (capabilities != null) {
            applyCapabilitiesConfig(capabilities);
        }
    }

    private void applyBaseUrlConfig(ConfigCore core) {
        if (core.getBaseUrl() != null) {
            String baseUrl = core.getBaseUrl().trim();
            log.debug("Setting baseUrl to {}", baseUrl);
            coreApi.baseUrl(baseUrl);
        }
    }

    private void applyProxyConfig(ConfigCore core) {
        if (core.getHttpProxy() != null && !core.getHttpProxy().trim().isEmpty()) {
            // TODO: support basic & ntlm auth
            try {
                String proxyUrlString = core.getHttpProxy().trim();
                URL proxyUrl = new URL(proxyUrlString);
                log.info("Setting httpProxy to {} {}", proxyUrl.getHost(), proxyUrl.getPort());
                coreApi.httpProxy(proxyUrl.getHost(), proxyUrl.getPort());
            } catch (MalformedURLException e) {
                log.error("Failed to parse http proxy URL {}", core.getHttpProxy().trim(), e);
            }
        }

        if (core.getHttpsProxy() != null && !core.getHttpsProxy().trim().isEmpty()) {
            // TODO: support basic & ntlm auth
            try {
                String proxyUrlString = core.getHttpProxy().trim();
                URL proxyUrl = new URL(proxyUrlString);
                log.info("Setting httpsProxy to {} {}", proxyUrl.getHost(), proxyUrl.getPort());
                coreApi.httpsProxy(proxyUrl.getHost(), proxyUrl.getPort());
            } catch (MalformedURLException e) {
                log.error("Failed to parse https proxy URL {}", core.getHttpsProxy().trim(), e);
            }
        }

        if (core.getNonProxyHosts() != null && !core.getNonProxyHosts().trim().isEmpty()) {
            String noProxyHostsString = core.getNonProxyHosts().trim();
            String[] noProxyHosts = Arrays.stream(noProxyHostsString.split(","))
                    .map(String::trim)
                    .filter(host -> !host.isEmpty())
                    .toArray(String[]::new);

            log.info("Setting nonProxyHosts to {}", String.join(",", noProxyHosts));
            coreApi.nonProxyHosts(noProxyHosts);
        }
    }

    private void applyCapabilitiesConfig(List<ConfigCapability> capabilities) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        for (ConfigCapability capabilityConfig : capabilities) {
            CapabilityType type = CapabilityType.capabilityType(capabilityConfig.getType());
            log.info("type={}", type.toString());
            CapabilityReference existing = capabilityRegistry.getAll().stream()
                    .filter(cap -> cap.context().type().equals(type))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                boolean enabled = capabilityConfig.getEnabled() == null ? existing.context().isEnabled() : capabilityConfig.getEnabled();
                CapabilityIdentity id = getCapabilityId(existing);

                log.info("Updating capability of type {} and id {}", capabilityConfig.getType(), id);

                capabilityRegistry.update(
                        id,
                        enabled,
                        capabilityConfig.getNotes(),
                        capabilityConfig.getAttributes()
                );
            } else {
                log.info("Creating capability of type {}", capabilityConfig.getType());

                boolean enabled = capabilityConfig.getEnabled() == null ? true : capabilityConfig.getEnabled();
                capabilityRegistry.add(
                        type,
                        enabled,
                        capabilityConfig.getNotes(),
                        capabilityConfig.getAttributes()
                );
            }
        }
    }

    private CapabilityIdentity getCapabilityId(CapabilityReference existing) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = existing.getClass().getMethod("id");
        return (CapabilityIdentity) m.invoke(existing);
    }

    private void applyRepositoryConfig(ConfigRepository repository) {
        if (repository.getBlobStores() != null) {
            repository.getBlobStores().forEach(configBlobStore -> {
                if (configBlobStore.getAttributes().get("file") == null
                        || configBlobStore.getAttributes().get("file").get("path") == null
                        || !(configBlobStore.getAttributes().get("file").get("path") instanceof String)) {
                    log.error(".attributes.file.path of blob store {} must be a string!", configBlobStore.getName());
                    return;
                }

                BlobStore existingBlobStore = blobStoreManager.get(configBlobStore.getName());

                if (existingBlobStore != null) {
                    BlobStoreConfiguration existingBlobStoreConfig = existingBlobStore.getBlobStoreConfiguration();

                    if (!configBlobStore.getAttributes().get("file").get("path").equals(
                            existingBlobStoreConfig.getAttributes().get("file").get("path")
                    )) {
                        log.error("Can not update .attributes.file.path for blob stores. Blob store {}, current path: {}, new path {}",
                                configBlobStore.getName(), existingBlobStoreConfig.getAttributes().get("file").get("path"),
                                configBlobStore.getAttributes().get("file").get("path"));
                        return;
                    }

                    if (!configBlobStore.getType().equals(existingBlobStoreConfig.getType())) {
                        log.error("Can not update type of blob stores. Blob store {}, current type: {}, new type {}",
                                configBlobStore.getName(), existingBlobStoreConfig.getType(),
                                configBlobStore.getType());
                        return;
                    }

                    existingBlobStoreConfig.setAttributes(configBlobStore.getAttributes());

                    try {
                        blobStoreManager.update(existingBlobStoreConfig);
                    } catch (Exception e) {
                        log.error("Could not update blob store {}", configBlobStore.getName(), e);
                    }
                } else {
                    BlobStoreConfiguration config = new BlobStoreConfiguration();
                    config.setName(configBlobStore.getName());
                    config.setAttributes(configBlobStore.getAttributes());
                    config.setType(configBlobStore.getType());
                    try {
                        blobStoreManager.create(config);
                    } catch (Exception e) {
                        log.error("Could not create blob store {}", configBlobStore.getName(), e);
                    }
                }
            });
        } else if (repository.getPruneBlobStores() != null && repository.getPruneBlobStores()) {
            log.warn("repository.pruneBlobStores has no effect when no blob stores are configured!");

        }

        if (repository.getCleanupPolicies() != null) {
            repository.getCleanupPolicies().forEach(cp -> {
                CleanupPolicy existingCp = cleanupPolicyStorage.get(cp.getName());

                if (existingCp != null) {
                    existingCp.setCriteria(cp.getCriteria());
                    existingCp.setFormat(cp.getFormat());
                    existingCp.setNotes(cp.getNotes());
                    existingCp.setMode(cp.getMode());
                    cleanupPolicyStorage.update(existingCp);
                } else {
                    cleanupPolicyStorage.add(new CleanupPolicy(cp.getName(), cp.getNotes(), cp.getFormat(), cp.getMode(), cp.getCriteria()));
                }
            });

            if (repository.getPruneCleanupPolicies() != null && repository.getPruneCleanupPolicies()) {
                cleanupPolicyStorage.getAll().forEach(existingCp -> {
                    if (repository.getCleanupPolicies().stream().noneMatch(cp -> existingCp.getName().equals(cp.getName()))) {
                        log.info("Pruning cleanup policy {}", existingCp.getName());
                        cleanupPolicyStorage.remove(existingCp);
                    }
                });
            }
        } else if (repository.getPruneCleanupPolicies() != null && repository.getPruneCleanupPolicies()) {
            log.warn("repository.pruneCleanupPolicies has no effect when no cleanup policies are configured!");
        }

        if (repository.getRepositories() != null) {
            repository.getRepositories().forEach(repoConfig -> {
                Repository existingRepo = repositoryManager.get(repoConfig.getName());

                if (existingRepo != null) {
                    if (!existingRepo.getConfiguration().getRecipeName().equals(repoConfig.getRecipeName())) {
                        log.error("Can not change recipeName of repo {}", repoConfig.getName());
                        return;
                    }

                    Configuration configuration = existingRepo.getConfiguration();
                    configuration.setAttributes(repoConfig.getAttributes());
                    if (repoConfig.getOnline() != null) {
                        configuration.setOnline(repoConfig.getOnline());
                    }

                    try {
                        repositoryManager.update(configuration);
                    } catch (Exception e) {
                        log.error("Failed to update repo {}", repoConfig.getName(), e);
                    }
                } else {
                    Configuration configuration = new Configuration();
                    configuration.setRepositoryName(repoConfig.getName());
                    configuration.setRecipeName(repoConfig.getRecipeName());
                    configuration.setAttributes(repoConfig.getAttributes());
                    configuration.setOnline(repoConfig.getOnline() != null ? repoConfig.getOnline() : true);

                    try {
                        repositoryManager.create(configuration);
                    } catch (Exception e) {
                        log.error("Failed to create repo {}", repoConfig.getName(), e);
                    }
                }
            });

            if (repository.getPruneRepositories() != null && repository.getPruneRepositories()) {
                repositoryManager.browse().forEach(existingRepo -> {
                    if (repository.getRepositories().stream().noneMatch(repo -> existingRepo.getName().equals(repo.getName()))) {
                        log.info("Pruning repository {}", existingRepo.getName());
                        log.info(existingRepo.getConfiguration().toString());
                        try {
                            repositoryManager.delete(existingRepo.getName());
                        } catch (Exception e) {
                            log.error("Failed to delete repo {}", existingRepo.getName(), e);
                        }
                    }
                });
            }
        } else if (repository.getPruneRepositories() != null && repository.getPruneRepositories()) {
            log.warn("repository.pruneRepositories has no effect when no repositories are configured!");
        }

        // we prune blob stores here as pruned repos might rely on them
        if (repository.getBlobStores() != null && repository.getPruneBlobStores() != null && repository.getPruneBlobStores()) {
            blobStoreManager.browse().forEach(existingBlobStore -> {
                String name = existingBlobStore.getBlobStoreConfiguration().getName();
                if (repository.getBlobStores().stream().noneMatch(blobStore -> blobStore.getName().equals(name))) {
                    log.info("pruning blob store {}", name);
                    try {
                        blobStoreManager.delete(name);
                    } catch (Exception e) {
                        log.error("Failed to prune blob store {}", name, e);
                    }
                }
            });
        }
    }

    /**
     * Hack approach to SecurityApiImpl.getSecuritySystem as we can't use that class as otherwise
     * the osgi subsystem will yell at us 🤦‍
     */
    private SecuritySystem resolveSecuritySystem(SecurityApi securityApi) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method m = securityApi.getClass().getMethod("getSecuritySystem");
        return (SecuritySystem) m.invoke(securityApi);
    }

    /**
     * Apply all configs related to security
     *
     * @param security The security config
     */
    private void applySecurityConfig(ConfigSecurity security) {
        if (security.getAnonymousAccess() != null) {
            securityApi.setAnonymousAccess(security.getAnonymousAccess());
        }

        if (security.getRealms() != null) {
            security.getRealms().forEach(realm -> {
                if (realm.getEnabled() != null) {
                    if (realm.getEnabled()) {
                        realmManager.enableRealm(realm.getName(), true);
                    } else {
                        realmManager.disableRealm(realm.getName());
                    }
                } else {
                    log.warn("Passing a realm with enabled: null doesn't make sense...");
                }
            });
        }

        if (security.getUsers() != null) {
            security.getUsers().forEach(userConfig -> {
                User existingUser = null;
                try {
                    existingUser = securitySystem.getUser(userConfig.getUsername());
                } catch (UserNotFoundException e) {
                    // ignore
                }

                if (existingUser != null) {
                    log.info("User {} already exists. Patching it...", userConfig.getUsername());
                    existingUser.setFirstName(userConfig.getFirstName());
                    existingUser.setLastName(userConfig.getLastName());
                    existingUser.setEmailAddress(userConfig.getEmail());

                    if (userConfig.getActive() != null) {
                        if (userConfig.getActive()) {
                            if (existingUser.getStatus() == UserStatus.disabled) {
                                log.info("Reactivating user {}", existingUser.getUserId());
                                existingUser.setStatus(UserStatus.active);
                            } else if (existingUser.getStatus() != UserStatus.active) {
                                log.error("Can not activate user {} ({}) with state {}", existingUser.getUserId(), existingUser.getSource(), existingUser.getStatus());
                            }
                        } else {
                            if (existingUser.getStatus() != UserStatus.disabled) {
                                log.info("Disabling user {} ({}) with state {}", existingUser.getUserId(), existingUser.getSource(), existingUser.getStatus());
                                existingUser.setStatus(UserStatus.disabled);
                            }
                        }
                    }

                    if (userConfig.getUpdateExistingPassword() != null && userConfig.getUpdateExistingPassword()) {
                        try {
                            securitySystem.changePassword(existingUser.getUserId(), userConfig.getPassword());
                        } catch (UserNotFoundException e) {
                            log.error("Failed to update password of user {}", existingUser.getUserId(), e);
                        }
                    }

                    existingUser.setRoles(userConfig.getRoles().stream().map(r -> new RoleIdentifier(r.getSource(), r.getRole())).collect(Collectors.toSet()));
                    try {
                        securitySystem.updateUser(existingUser);
                    } catch (UserNotFoundException | NoSuchUserManagerException e) {
                        log.error("Could not update user {}", userConfig.getUsername(), e);
                    }
                } else {
                    log.info("User {} does not yet exist. Creating it...", userConfig.getUsername());
                    securityApi.addUser(
                            userConfig.getUsername(),
                            userConfig.getFirstName(),
                            userConfig.getLastName(),
                            userConfig.getEmail(),
                            userConfig.getActive() != null ? userConfig.getActive() : true,
                            userConfig.getPassword(),
                            userConfig.getRoles().stream().map(ConfigSecurityRole::getRole).collect(Collectors.toList())
                    );
                }
            });

            if (security.getPruneUsers() != null && security.getPruneUsers()) {
                Set<User> existingUsers = securitySystem.searchUsers(new UserSearchCriteria());

                existingUsers.forEach(existingUser -> {
                    if (security.getUsers().stream().noneMatch(u -> existingUser.getUserId().equals(u.getUsername()))) {
                        log.info("Pruning user {} ...", existingUser.getUserId());
                        try {
                            securitySystem.deleteUser(existingUser.getUserId(), existingUser.getSource());
                        } catch (NoSuchUserManagerException | UserNotFoundException e) {
                            log.error("Failed to prune user {} ({})", existingUser.getUserId(), existingUser.getSource(), e);
                        }
                    }
                });
            }
        } else if (security.getPruneUsers() != null && security.getPruneUsers()) {
            log.error("security.pruneUsers has no effect when not specifying any users!");
        }
    }


}
