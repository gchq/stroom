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

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;


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
        this.authenticationConfig = Objects.requireNonNullElseGet(
                authenticationConfig, ProxyAuthenticationConfig::new);
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
