/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.AssertTrue;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ProxyAuthenticationConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_AUTHENTICATION_REQUIRED = "authenticationRequired";
    public static final String PROP_NAME_OPENID = "openId";
    public static final boolean DEFAULT_IS_AUTHENTICATION_REQUIRED = true;

    private final boolean authenticationRequired;
    private final ProxyOpenIdConfig openIdConfig;

    public ProxyAuthenticationConfig() {
        authenticationRequired = DEFAULT_IS_AUTHENTICATION_REQUIRED;
        openIdConfig = new ProxyOpenIdConfig();
    }

    @JsonCreator
    public ProxyAuthenticationConfig(
            @JsonProperty(PROP_NAME_AUTHENTICATION_REQUIRED) final Boolean authenticationRequired,
            @JsonProperty(PROP_NAME_OPENID) final ProxyOpenIdConfig openIdConfig) {

        this.authenticationRequired = Objects.requireNonNullElse(
                authenticationRequired, DEFAULT_IS_AUTHENTICATION_REQUIRED);
        this.openIdConfig = Objects.requireNonNullElseGet(openIdConfig, ProxyOpenIdConfig::new);
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
