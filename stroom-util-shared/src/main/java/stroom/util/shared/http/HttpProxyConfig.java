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

package stroom.util.shared.http;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class HttpProxyConfig {

    @JsonProperty
    private final String host;
    @JsonProperty
    private final Integer port;
    @JsonProperty
    private final String scheme;
    @JsonProperty
    private final HttpAuthConfig auth;
    @JsonProperty
    private final List<String> nonProxyHosts;

    @JsonCreator
    public HttpProxyConfig(@JsonProperty("host") final String host,
                           @JsonProperty("port") final Integer port,
                           @JsonProperty("scheme") final String scheme,
                           @JsonProperty("auth") final HttpAuthConfig auth,
                           @JsonProperty("nonProxyHosts") final List<String> nonProxyHosts) {
        this.host = NullSafe.requireNonNullElse(host, "");
        this.port = NullSafe.requireNonNullElse(port, -1);
        this.scheme = NullSafe.requireNonNullElse(scheme, "http");
        this.auth = auth;
        this.nonProxyHosts = nonProxyHosts;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getScheme() {
        return scheme;
    }

    public List<String> getNonProxyHosts() {
        return nonProxyHosts;
    }

    public HttpAuthConfig getAuth() {
        return auth;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpProxyConfig that = (HttpProxyConfig) o;
        return Objects.equals(host, that.host) &&
               Objects.equals(port, that.port) &&
               Objects.equals(scheme, that.scheme) &&
               Objects.equals(auth, that.auth) &&
               Objects.equals(nonProxyHosts, that.nonProxyHosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, scheme, auth, nonProxyHosts);
    }

    @Override
    public String toString() {
        return "HttpProxyConfig{" +
               "host='" + host + '\'' +
               ", port=" + port +
               ", scheme='" + scheme + '\'' +
               ", auth=" + auth +
               ", nonProxyHosts=" + nonProxyHosts +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<HttpProxyConfig, Builder> {

        private String host = "";
        private Integer port = -1;
        private String scheme = "http";
        private HttpAuthConfig auth;
        private List<String> nonProxyHosts;

        private Builder() {
        }

        private Builder(final HttpProxyConfig httpProxyConfig) {
            host = httpProxyConfig.host;
            port = httpProxyConfig.port;
            scheme = httpProxyConfig.scheme;
            auth = httpProxyConfig.auth;
            nonProxyHosts = httpProxyConfig.nonProxyHosts;
        }

        public Builder host(final String host) {
            this.host = host;
            return self();
        }

        public Builder port(final Integer port) {
            this.port = port;
            return self();
        }

        public Builder scheme(final String scheme) {
            this.scheme = scheme;
            return self();
        }

        public Builder auth(final HttpAuthConfig auth) {
            this.auth = auth;
            return self();
        }

        public Builder nonProxyHosts(final List<String> nonProxyHosts) {
            this.nonProxyHosts = nonProxyHosts;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public HttpProxyConfig build() {
            return new HttpProxyConfig(
                    host,
                    port,
                    scheme,
                    auth,
                    nonProxyHosts);
        }
    }
}
