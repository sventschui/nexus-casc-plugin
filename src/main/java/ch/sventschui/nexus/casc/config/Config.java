package ch.sventschui.nexus.casc.config;

import java.util.List;

public class Config {
    private ConfigCore core;
    private ConfigRepository repository;
    private ConfigSecurity security;
    private List<ConfigCapability> capabilities;

    public ConfigCore getCore() {
        return core;
    }

    public void setCore(ConfigCore core) {
        this.core = core;
    }

    public ConfigRepository getRepository() {
        return repository;
    }

    public void setRepository(ConfigRepository repository) {
        this.repository = repository;
    }

    public ConfigSecurity getSecurity() {
        return security;
    }

    public void setSecurity(ConfigSecurity security) {
        this.security = security;
    }

    public List<ConfigCapability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<ConfigCapability> capabilities) {
        this.capabilities = capabilities;
    }
}
