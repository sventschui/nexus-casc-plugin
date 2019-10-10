# Nexus Configuration as Code

Nexus CasC is a configuration as code plugin for sonatype nexus 3.

This plugin allows to specify a YAML file to configure a Nexus instance on startup.

## Usage

### Docker

When using docker the easiest way to get started is to use the `sventschui/nexus-casc` image that inherits form `sonatype/nexus3`.

The docker image supports the same options as the `sonatype/nexus3` image plus the following additions.

It expects a YAML configuration file to be mounted to `/opt/nexus.yml` (This path can be overridden using the `NEXUS_CASC_CONFIG` env var).

The format of the YAML file is documented below.

### Manual installation

Download the Nexus CasC plugin [here](https://github.com/sventschui/nexus-casc-plugin/releases) and copy it into the `systems` folder of your nexus installation.
This folder resides in `/opt/sonatype/nexus/system/` when using the `sonatype/nexus3` docker image.

Append the following line to the `etc/karaf/startup.properties` (`/opt/sonatype/nexus/etc/karaf/startup.properties` in the `sonatype/nexus3` docker image) file. 
Replace the `<NEXUS_CASC_VERSION>` placeholder with the version of the Nexus CasC plugin you downloaded. 

```
reference\:file\:nexus-casc-plugin-<NEXUS_CASC_VERSION>.jar = 199
```

Create a YAML configuration file (as documented below) and add its path to the `NEXUS_CASC_CONFIG`
environment variable.

Now you can start Nexus as usual.

## Configuration file

You can find an example configuration file [here](https://github.com/sventschui/nexus-casc-plugin/blob/master/default-nexus.yml).

### Interpolation 

Use `${ENV_VAR}` for env var interpolation. Use `${ENV_VAR:default}` or `${ENV_VAR:"default"}` for default values.

Use `${file:/path/to/a/file}` to include the contents of a file.

The configuration file supports following options:

### Supported options

#### Core

```yaml
core:
  baseUrl: "" # Nexus base URL
  httpProxy: "" # HTTP proxy (Note: Basic Auth and NTLM are not yet supported, file an issue if you require this)
  httpsProxy: ""  # HTTP proxy
  nonProxyHosts: "" # Comma separated list of hosts not to be queried through a proxy
```

#### Security

```yaml
security:
  anonymousAccess: false # Enable/Disable anonymous access
  pruneUsers: true # True to delete users not part of this configuration file
  realms: # Authentication realms, tested for rutauth-realm only
    - name: rutauth-realm
      enabled: true
  users:
    - username: johndoe
      firstName: John
      lastName: Doe
      password: ${file:/run/secrets/password_johndoe}
      updateExistingPassword: false # True to update passwords of existing users, otherwise password is only used when creating a user
      email: johndoe@example.org
      roles:
        - source: ""
          role: nx-admin
```


#### Repository

```yaml
repository:
  pruneBlobStores: true # True to delete blob stores not present in this configuration file
  blobStores: # List of blob stores to create
    - name: maven
      type: File
      attributes:
        file:
          path: maven
        blobStoreQuotaConfig:
          quotaLimitBytes: 10240000000
          quotaType: spaceUsedQuota
    - name: npm
      type: File
      attributes:
        file:
          path: npm
        blobStoreQuotaConfig:
          quotaLimitBytes: 10240000000
          quotaType: spaceUsedQuota
    - name: docker
      type: File
      attributes:
        file:
          path: docker
        blobStoreQuotaConfig:
          quotaLimitBytes: 10240000000
          quotaType: spaceUsedQuota
  pruneCleanupPolicies: true # True to delete cleanup policies not present in this configuration file 
  cleanupPolicies:
    - name: cleanup-maven-proxy
      format: maven2
      notes: ''
      criteria:
        lastDownloadBefore: 10
    - name: cleanup-npm-proxy
      format: npm
      notes: ''
      criteria:
        lastDownloadBefore: 10
    - name: cleanup-docker-proxy
      format: docker
      notes: ''
      criteria:
        lastDownloaded: 864000
  pruneRepositories: true # True to delete repositories not present in this configuration file
  repositories:
    - name: npm-proxy
      online: true
      recipeName: npm-proxy
      attributes:
        proxy:
          remoteUrl: https://registry.npmjs.org
          contentMaxAge: -1.0
          metadataMaxAge: 1440.0
        httpclient:
          blocked: false
          autoBlock: true
          connection:
            useTrustStore: false
        storage:
          blobStoreName: npm
          strictContentTypeValidation: true
        routingRules:
          routingRuleId: null
        negativeCache:
          enabled: true
          timeToLive: 1440.0
        cleanup:
          policyName: cleanup-npm-proxy
    - name: npm-hosted
      online: true
      recipeName: npm-hosted
      attributes:
        storage:
          blobStoreName: npm
          strictContentTypeValidation: true
          writePolicy: ALLOW_ONCE
        cleanup:
          policyName: None
    - name: npm
      online: true
      recipeName: npm-group
      attributes:
        storage:
          blobStoreName: npm
          strictContentTypeValidation: true
        group:
          memberNames:
           - "npm-proxy"
           - "npm-hosted"
    - name: maven-snapshots
      online: true
      recipeName: maven2-hosted
      attributes:
        maven:
          versionPolicy: SNAPSHOT
          layoutPolicy: STRICT
        storage:
          writePolicy: ALLOW
          strictContentTypeValidation: false
          blobStoreName: maven
    - name: maven-central
      online: true
      recipeName: maven2-proxy
      attributes:
        proxy:
          contentMaxAge: -1
          remoteUrl: https://repo1.maven.org/maven2/
          metadataMaxAge: 1440
        negativeCache:
          timeToLive: 1440
          enabled: true
        storage:
          strictContentTypeValidation: false
          blobStoreName: maven
        httpClient:
          connection:
            blocked: false
            autoBlock: true
        maven:
          versionPolicy: RELEASE
          layoutPolicy: PERMISSIVE
        cleanupPolicy:
          name: cleanup-maven-proxy
        httpclient:
        maven-indexer:
    - name: maven-tudelft
      online: true
      recipeName: maven2-proxy
      attributes:
        proxy:
          contentMaxAge: -1
          remoteUrl: https://simulation.tudelft.nl/maven/
          metadataMaxAge: 1440
        negativeCache:
          timeToLive: 1440
          enabled: true
        storage:
          strictContentTypeValidation: false
          blobStoreName: maven
        httpClient:
          connection:
            blocked: false
            autoBlock: true
        maven:
          versionPolicy: RELEASE
          layoutPolicy: PERMISSIVE
        cleanupPolicy:
          name: cleanup-maven-proxy
        httpclient:
        maven-indexer:
    - name: maven-public
      online: true
      recipeName: maven2-group
      attributes:
        maven:
          versionPolicy: MIXED
        group:
          memberNames:
           - "maven-central"
           - "maven-snapshots"
           - "maven-tudelft"
        storage:
          blobStoreName: maven
    - name: docker-hosted
      online: true
      recipeName: docker-hosted
      attributes:
        docker:
          forceBasicAuth: true
          v1Enabled: false
        storage:
          blobStoreName: docker
          strictContentTypeValidation: true
          writePolicy: ALLOW_ONCE
        cleanup:
          policyName: None
    - name: docker-proxy
      online: true
      recipeName: docker-proxy
      attributes:
        docker:
          forceBasicAuth: true
          v1Enabled: false
        proxy:
          remoteUrl: https://registry-1.docker.io
          contentMaxAge: -1.0
          metadataMaxAge: 1440.0
        dockerProxy:
          indexType: REGISTRY
        httpclient:
          blocked: false
          autoBlock: true
          connection:
            useTrustStore: false
        storage:
          blobStoreName: docker
          strictContentTypeValidation: true
        routingRules:
          routingRuleId: null
        negativeCache:
          enabled: true
          timeToLive: 1440.0
        cleanup:
          policyName: cleanup-docker-proxy
    - name: docker
      online: true
      recipeName: docker-group
      attributes:
        docker:
          forceBasicAuth: true
          v1Enabled: false
        storage:
          blobStoreName: docker
          strictContentTypeValidation: true
        group:
          memberNames:
            - "docker-hosted"
            - "docker-proxy"
```
