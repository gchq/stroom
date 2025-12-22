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

package stroom.util.http;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * Configuration of access to a remote host through a proxy server
 * <p/>
 * <b>Configuration Parameters:</b>
 * <table>
 *     <tr>
 *         <td>Name</td>
 *         <td>Default</td>
 *         <td>Description</td>
 *     </tr>
 *     <tr>
 *         <td>{@code host}</td>
 *         <td>REQUIRED</td>
 *         <td>The proxy server host name or ip address.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code port}</td>
 *         <td>scheme default</td>
 *         <td>The proxy server port. If the port is not set then the scheme default port is used.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code scheme}</td>
 *         <td>http</td>
 *         <td>The proxy server URI scheme. HTTP and HTTPS schemas are permitted. By default HTTP scheme is used.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code auth}</td>
 *         <td>(none)</td>
 *         <td>
 *             The proxy server {@link io.dropwizard.client.proxy.AuthConfiguration} BASIC authentication credentials.
 *             If they are not set then no credentials will be passed to the server.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code nonProxyHosts}</td>
 *         <td>(none)</td>
 *         <td>
 *             List of patterns of hosts that should be reached without proxy.
 *             The patterns may contain symbol '*' as a wildcard.
 *             If a host matches one of the patterns it will be reached through a direct connection.
 *         </td>
 *     </tr>
 * </table>
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class HttpProxyConfiguration extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    private final String host;
    private final Integer port;
    private final String scheme;
    private final HttpAuthConfiguration auth;
    private final List<String> nonProxyHosts;

    public HttpProxyConfiguration() {
        host = "";
        port = -1;
        scheme = "http";
        auth = null;
        nonProxyHosts = null;
    }

    @JsonCreator
    public HttpProxyConfiguration(@JsonProperty("host") final String host,
                                  @JsonProperty("port") final Integer port,
                                  @JsonProperty("scheme") final String scheme,
                                  @JsonProperty("auth") final HttpAuthConfiguration auth,
                                  @JsonProperty("nonProxyHosts") final List<String> nonProxyHosts) {
        this.host = Objects
                .requireNonNullElse(host, "");
        this.port = Objects
                .requireNonNullElse(port, -1);
        this.scheme = Objects
                .requireNonNullElse(scheme, "http");
        this.auth = auth;
        this.nonProxyHosts = nonProxyHosts;
    }

    @JsonProperty
    public String getHost() {
        return host;
    }


    @JsonProperty
    public Integer getPort() {
        return port;
    }

    @JsonProperty
    public String getScheme() {
        return scheme;
    }

    @JsonProperty
    public List<String> getNonProxyHosts() {
        return nonProxyHosts;
    }

    public HttpAuthConfiguration getAuth() {
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
        final HttpProxyConfiguration that = (HttpProxyConfiguration) o;
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
        return "HttpProxyConfiguration{" +
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
        return new Builder(new HttpProxyConfiguration());
    }

    public static class Builder extends AbstractBuilder<HttpProxyConfiguration, HttpProxyConfiguration.Builder> {

        private String host;
        private Integer port;
        private String scheme;
        private HttpAuthConfiguration auth;
        private List<String> nonProxyHosts;

        public Builder() {
            this(new HttpProxyConfiguration());
        }

        public Builder(final HttpProxyConfiguration httpProxyConfiguration) {
            host = httpProxyConfiguration.host;
            port = httpProxyConfiguration.port;
            scheme = httpProxyConfiguration.scheme;
            auth = httpProxyConfiguration.auth;
            nonProxyHosts = httpProxyConfiguration.nonProxyHosts;
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

        public Builder auth(final HttpAuthConfiguration auth) {
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

        public HttpProxyConfiguration build() {
            return new HttpProxyConfiguration(
                    host,
                    port,
                    scheme,
                    auth,
                    nonProxyHosts);
        }
    }
}
