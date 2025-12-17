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

package stroom.util.net;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public abstract class UriConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_SCHEME = "scheme";
    public static final String PROP_NAME_HOSTNAME = "hostname";
    public static final String PROP_NAME_PORT = "port";
    public static final String PROP_NAME_PATH_PREFIX = "pathPrefix";

    private final String scheme;
    private final String hostname;
    private final Integer port;
    private final String pathPrefix;

    public UriConfig() {
        scheme = null;
        hostname = null;
        port = null;
        pathPrefix = null;
    }

    @JsonCreator
    public UriConfig(@JsonProperty(PROP_NAME_SCHEME) final String scheme,
                     @JsonProperty(PROP_NAME_HOSTNAME) final String hostname,
                     @JsonProperty(PROP_NAME_PORT) final Integer port,
                     @JsonProperty(PROP_NAME_PATH_PREFIX) final String pathPrefix) {
        this.scheme = scheme;
        this.hostname = hostname;
        this.port = port;
        this.pathPrefix = pathPrefix;
    }

    public UriConfig(final String scheme, final String hostname, final Integer port) {
        this(scheme, hostname, port, null);
    }

    public UriConfig(final String scheme, final String hostname) {
        this(scheme, hostname, null, null);
    }

    @Pattern(regexp = "^https?$")
    public String getScheme() {
        return scheme;
    }

    @ReadOnly
    @JsonProperty(PROP_NAME_HOSTNAME)
    public String getHostname() {
        return hostname;
    }

    @Min(0)
    @Max(65535)
    @JsonProperty(PROP_NAME_PORT)
    public Integer getPort() {
        return port;
    }

    @JsonProperty(PROP_NAME_PATH_PREFIX)
    @Pattern(regexp = "/[^/]+")
    @JsonPropertyDescription(
            "Any prefix to be added to the beginning of paths for this URI. " +
            "This may be needed if there is some form of gateway in front of Stroom that requires different paths.")
    public String getPathPrefix() {
        return pathPrefix;
    }

    public String asUri() {
        final StringBuilder sb = new StringBuilder();
        if (NullSafe.isNonBlankString(scheme)) {
            sb.append(scheme.trim())
                    .append("://");
        }

        if (NullSafe.isNonBlankString(hostname)) {
            sb.append(hostname.trim());
        }

        if (port != null) {
            sb.append(":")
                    .append(port);
        }

        final String trimmedPathPrefix = NullSafe.trim(pathPrefix);
        if (!trimmedPathPrefix.isEmpty() && !trimmedPathPrefix.equals("/")) {
            if (!trimmedPathPrefix.startsWith("/")) {
                sb.append("/");
            }
            sb.append(trimmedPathPrefix);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return asUri();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final UriConfig uriConfig)) {
            return false;
        }
        return Objects.equals(scheme, uriConfig.scheme) &&
               Objects.equals(hostname, uriConfig.hostname) &&
               Objects.equals(port, uriConfig.port) &&
               Objects.equals(pathPrefix, uriConfig.pathPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, hostname, port, pathPrefix);
    }


}
