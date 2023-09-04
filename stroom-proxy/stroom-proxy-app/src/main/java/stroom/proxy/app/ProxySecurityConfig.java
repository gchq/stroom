package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ProxySecurityConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_AUTHENTICATION = "authentication";

    private final ProxyAuthenticationConfig authenticationConfig;

    public ProxySecurityConfig() {
        authenticationConfig = new ProxyAuthenticationConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxySecurityConfig(
            @JsonProperty(PROP_NAME_AUTHENTICATION) final ProxyAuthenticationConfig authenticationConfig) {
        this.authenticationConfig = authenticationConfig;
    }

    @JsonProperty(PROP_NAME_AUTHENTICATION)
    public ProxyAuthenticationConfig getAuthenticationConfig() {
        return authenticationConfig;
    }

    @Override
    public String toString() {
        return "ProxySecurityConfig{" +
                "authenticationConfig=" + authenticationConfig +
                '}';
    }
}
