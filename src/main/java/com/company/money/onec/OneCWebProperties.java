package com.company.money.onec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "onec.web")
public class OneCWebProperties {

    private String baseUrl = "";
    private String username = "";
    private String password = "";
    private String organizationKey = "c1405bb8-2942-11e5-874a-001e101f4da1";

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public String getOrganizationKey() {
        return organizationKey;
    }

    public void setOrganizationKey(String organizationKey) {
        this.organizationKey = organizationKey == null ? "" : organizationKey.trim();
    }
}
