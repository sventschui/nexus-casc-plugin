package ch.sventschui.nexus.casc.config;

import java.util.List;

public class ConfigSecurity {
    private Boolean anonymousAccess;
    private Boolean pruneUsers;
    private List<ConfigSecurityUser> users;
    private List<ConfigSecurityRealm> realms;

    public Boolean getAnonymousAccess() {
        return anonymousAccess;
    }

    public void setAnonymousAccess(Boolean anonymousAccess) {
        this.anonymousAccess = anonymousAccess;
    }

    public Boolean getPruneUsers() {
        return pruneUsers;
    }

    public void setPruneUsers(Boolean pruneUsers) {
        this.pruneUsers = pruneUsers;
    }

    public List<ConfigSecurityUser> getUsers() {
        return users;
    }

    public void setUsers(List<ConfigSecurityUser> users) {
        this.users = users;
    }

    public List<ConfigSecurityRealm> getRealms() {
        return realms;
    }

    public void setRealms(List<ConfigSecurityRealm> realms) {
        this.realms = realms;
    }
}
