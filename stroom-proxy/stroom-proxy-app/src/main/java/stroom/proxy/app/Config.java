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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

public class Config extends Configuration {

    private ProxyConfig proxyConfig;

    @Valid
    private Map<String, JerseyClientConfiguration> jerseyClients = new HashMap<>();

    @JsonProperty("jerseyClients")
    public Map<String, JerseyClientConfiguration> getJerseyClients() {
        return jerseyClients;
    }

    public void setJerseyClients(final Map<String, JerseyClientConfiguration> jerseyClients) {
        this.jerseyClients = jerseyClients;
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    public void setProxyConfig(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }
}
