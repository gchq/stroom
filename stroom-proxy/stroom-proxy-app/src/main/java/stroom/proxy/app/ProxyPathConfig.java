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
import stroom.util.io.PathConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyPathConfig extends PathConfig implements IsProxyConfig {

    public static final String PROP_NAME_DATA = "data";
    public static final String DEFAULT_DATA_PATH = "data";

    @JsonProperty(PROP_NAME_DATA)
    private final String data;

    public ProxyPathConfig() {
        data = DEFAULT_DATA_PATH;
    }

    @JsonCreator
    public ProxyPathConfig(@JsonProperty("data") final String data,
                           @JsonProperty("home") final String home,
                           @JsonProperty("temp") final String temp) {
        super(home, temp);
        this.data = NullSafe.nonBlankStringElse(data, DEFAULT_DATA_PATH);
    }

    /**
     * Where data will be stored during processing.
     */
    @NotBlank
    @ReadOnly
    @JsonPropertyDescription(
            "By default data will be stored relative to home. This property can be used to override " +
            "that location.")
    public String getData() {
        return data;
    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @JsonPropertyDescription(
            "By default, unless configured otherwise, all other configured paths " +
            "(except proxyConfig.path.temp) will be relative to this directory. If this value is null then" +
            "Stroom-Proxy will use either of the following to derive proxyConfig.path.home: the directory of the " +
            "Stroom-proxy application JAR file or ~/.stroom-proxy. " +
            "It must be an absolute path and it does not support '~' or variable substitution like other paths.")
    public String getHome() {
        return super.getHome();
    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @JsonPropertyDescription(
            "This directory is used by stroom-proxy to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file. " +
            "If not set then Stroom-Proxy will use <SYSTEM TEMP>/stroom-proxy." +
            "It must be an absolute path and it does not support '~' or variable substitution like other paths.")
    public String getTemp() {
        return super.getTemp();
    }

    public ProxyPathConfig withHome(final String home) {
        return new ProxyPathConfig(data, home, getTemp());
    }

    public ProxyPathConfig withTemp(final String temp) {
        return new ProxyPathConfig(data, getHome(), temp);
    }
}
