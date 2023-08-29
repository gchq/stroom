package stroom.proxy.app;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.AssertTrue;

@JsonPropertyOrder(alphabetic = true)
public class ProxyAuthenticationConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_AUTHENTICATION_REQUIRED = "authenticationRequired";
    public static final String PROP_NAME_OPENID = "openId";

    private final boolean authenticationRequired;
    private final ProxyOpenIdConfig openIdConfig;

    public ProxyAuthenticationConfig() {
        authenticationRequired = true;
        openIdConfig = new ProxyOpenIdConfig();
    }

    @JsonCreator
    public ProxyAuthenticationConfig(
            @JsonProperty(PROP_NAME_AUTHENTICATION_REQUIRED) final boolean authenticationRequired,
            @JsonProperty(PROP_NAME_OPENID) final ProxyOpenIdConfig openIdConfig) {

        this.authenticationRequired = authenticationRequired;
        this.openIdConfig = openIdConfig;
    }

    @ReadOnly
    @JsonProperty(PROP_NAME_AUTHENTICATION_REQUIRED)
    @JsonPropertyDescription("Choose whether Stroom requires authenticated access. " +
            "Only intended for use in development or testing.")
    @AssertTrue(
            message = "All authentication is disabled. This should only be used in development or test environments.",
            payload = ValidationSeverity.Warning.class)
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @JsonProperty(PROP_NAME_OPENID)
    public ProxyOpenIdConfig getOpenIdConfig() {
        return openIdConfig;
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
                ", authenticationRequired=" + authenticationRequired +
                '}';
    }

    public static Builder builder() {
        return new Builder(new ProxyAuthenticationConfig());
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private boolean isAuthenticationRequired;
        private ProxyOpenIdConfig openIdConfig;

        public Builder(final ProxyAuthenticationConfig proxyAuthenticationConfig) {
            this.isAuthenticationRequired = proxyAuthenticationConfig.authenticationRequired;
            this.openIdConfig = proxyAuthenticationConfig.openIdConfig;
        }

        public Builder authenticationRequired(final boolean isAuthenticationRequired) {
            this.isAuthenticationRequired = isAuthenticationRequired;
            return this;
        }

        public Builder openIdConfig(final ProxyOpenIdConfig openIdConfig) {
            this.openIdConfig = openIdConfig;
            return this;
        }

        public ProxyAuthenticationConfig build() {
            return new ProxyAuthenticationConfig(isAuthenticationRequired, openIdConfig);
        }
    }

}
