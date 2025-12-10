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

package stroom.config.app;

import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.Configuration;
import io.dropwizard.util.Duration;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

// Can't use a JsonCreator for this as the superclass doesn't use 'JsonCreator
@JsonPropertyOrder(alphabetic = true)
public class Config extends Configuration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Config.class);

    private AppConfig appConfig;

    @Valid
    private Map<String, JerseyClientConfiguration> jerseyClients = buildDefaultJerseyConfig();

    public Config() {
    }

    public Config(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

//    public Config(@JsonProperty("appConfig") final AppConfig appConfig) {
//        this.appConfig = appConfig;
//        this.jerseyClients = buildDefaultJerseyConfig();
//    }

//    @JsonCreator
//    public Config(@JsonProperty("appConfig") final AppConfig appConfig,
//                  @JsonProperty("jerseyClients") @Valid final Map<String, JerseyClientConfiguration> jerseyClients) {
//        this.appConfig = appConfig;
//        this.jerseyClients = jerseyClients;
//    }

    @JsonProperty("jerseyClients")
    public Map<String, JerseyClientConfiguration> getJerseyClients() {
        return jerseyClients;
    }

    public void setJerseyClients(final Map<String, JerseyClientConfiguration> jerseyClients) {
        this.jerseyClients = jerseyClients;
    }


    /**
     * The de-serialised yaml config merged with the compile time defaults to provide
     * a full config tree. Should ONLY be used by classes involved with setting up the
     * config properties. It MUST NOT be used by classes to get configuration values. They
     * should instead inject AppConfig or its descendants.
     */
    @JsonProperty("appConfig")
    public AppConfig getYamlAppConfig() {
        return appConfig;
    }

    public void setYamlAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    private Map<String, JerseyClientConfiguration> buildDefaultJerseyConfig() {
        final Map<String, JerseyClientConfiguration> map = new HashMap<>();

        // Various OIDC providers don't like gzip encoded requests so turn it off
        final JerseyClientConfiguration openIdJerseyConfig = new JerseyClientConfiguration();
        openIdJerseyConfig.setGzipEnabledForRequests(false);
        map.put(JerseyClientName.OPEN_ID.name(), openIdJerseyConfig);

        final JerseyClientConfiguration defaultJerseyConfig = new JerseyClientConfiguration();
        defaultJerseyConfig.setTimeout(Duration.seconds(0));
        defaultJerseyConfig.setConnectionTimeout(Duration.seconds(30));
        defaultJerseyConfig.setConnectionRequestTimeout(Duration.seconds(30));

        return map;
    }

}
