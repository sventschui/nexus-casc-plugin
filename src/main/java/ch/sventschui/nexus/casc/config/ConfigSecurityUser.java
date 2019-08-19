package ch.sventschui.nexus.casc.config;

import java.util.List;

public class ConfigSecurityUser {
    private String username;
    private String firstName;
    private String lastName;
    private String password;
    private Boolean updateExistingPassword;
    private List<ConfigSecurityRole> roles;
    private String email;
    private Boolean active;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getUpdateExistingPassword() {
        return updateExistingPassword;
    }

    public void setUpdateExistingPassword(Boolean updateExistingPassword) {
        this.updateExistingPassword = updateExistingPassword;
    }

    public List<ConfigSecurityRole> getRoles() {
        return roles;
    }

    public void setRoles(List<ConfigSecurityRole> roles) {
        this.roles = roles;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
