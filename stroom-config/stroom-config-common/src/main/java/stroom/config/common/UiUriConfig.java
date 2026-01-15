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

package stroom.config.common;

import stroom.util.net.UriConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class UiUriConfig extends UriConfig {

    public UiUriConfig() {
        super("https", null, null, null);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public UiUriConfig(@JsonProperty("scheme") final String scheme,
                       @JsonProperty("hostname") final String hostname,
                       @JsonProperty("port") final Integer port,
                       @JsonProperty("pathPrefix") final String pathPrefix) {
        super(scheme, hostname, port, pathPrefix);
    }

    @Override
    @JsonPropertyDescription("The scheme, i.e. http or https")
    public String getScheme() {
        return super.getScheme();
    }

    @Override
    @JsonPropertyDescription("This is the hostname/DNS for where the UI is hosted if different to the public facing " +
                             "URI of the server, e.g. during development or some other deployments.")
    public String getHostname() {
        return super.getHostname();
    }

    @Override
    @JsonPropertyDescription("This is the port to use for UI traffic, if different from the public facing URI.")
    public Integer getPort() {
        return super.getPort();
    }

    @Override
    @JsonPropertyDescription("An optional prefix to the base path. This may be needed when the UI communication" +
                             "goes via some form of gateway where the paths are mapped to something else.")
    public String getPathPrefix() {
        return super.getPathPrefix();
    }
}
