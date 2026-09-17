/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Configuration for deployments where an authenticating reverse proxy in front of stroom - not
 * stroom itself - is the OIDC Relying Party. E.g. an AWS Application Load Balancer with an
 * {@code authenticate-oidc}/{@code authenticate-cognito} listener rule, or NGINX fronted by
 * oauth2-proxy. The proxy completes the OIDC flow and injects a verified credential
 * (e.g. {@code x-amzn-oidc-data} or {@code Authorization: Bearer ...}) into each proxied request.
 * <p>
 * When enabled, stroom never initiates an OIDC flow of its own (the edge owns the flow), treats
 * request-token identities on browser-issued requests as ambient for CSRF purposes, and can
 * expire the edge's session cookies on logout.
 * </p>
 * <p>
 * See {@code stroom-security/AUTHENTICATION_DESIGN.md}.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class EdgeAuthenticationConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_ENABLED = "enabled";
    public static final String PROP_NAME_LOGOUT = "logout";

    private static final boolean DEFAULT_ENABLED = false;

    private final boolean enabled;
    private final EdgeLogoutConfig logout;

    public EdgeAuthenticationConfig() {
        enabled = DEFAULT_ENABLED;
        logout = new EdgeLogoutConfig();
    }

    @JsonCreator
    public EdgeAuthenticationConfig(
            @JsonProperty(PROP_NAME_ENABLED) final Boolean enabled,
            @JsonProperty(PROP_NAME_LOGOUT) final EdgeLogoutConfig logout) {
        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_ENABLED);
        this.logout = Objects.requireNonNullElseGet(logout, EdgeLogoutConfig::new);
    }

    @JsonProperty(PROP_NAME_ENABLED)
    @JsonPropertyDescription(
            "Set to true when an authenticating reverse proxy in front of stroom (e.g. an AWS " +
            "Application Load Balancer with an authenticate-oidc/authenticate-cognito rule, or " +
            "NGINX with oauth2-proxy) is the OIDC Relying Party. Stroom will then never initiate " +
            "an OIDC flow of its own and will treat request-token identities on browser-issued " +
            "requests as ambient for CSRF purposes. Requires identityProviderType EXTERNAL_IDP. " +
            "Note that with this enabled ALL browser access must traverse the proxy; API keys and " +
            "bearer tokens still work for direct machine access.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty(PROP_NAME_LOGOUT)
    public EdgeLogoutConfig getLogout() {
        return logout;
    }

    @Override
    public String toString() {
        return "EdgeAuthenticationConfig{" +
               "enabled=" + enabled +
               ", logout=" + logout +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EdgeAuthenticationConfig that = (EdgeAuthenticationConfig) o;
        return enabled == that.enabled
               && Objects.equals(logout, that.logout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, logout);
    }
}
