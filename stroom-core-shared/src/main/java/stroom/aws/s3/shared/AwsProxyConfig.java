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

package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AwsProxyConfig {

    @JsonProperty
    private final String host;
    @JsonProperty
    private final int port;
    @JsonProperty
    private final String scheme;
    @JsonProperty
    private final String username;
    @JsonProperty
    private final String password;
    @JsonProperty
    private final Boolean useSystemPropertyValues;

    @JsonCreator
    public AwsProxyConfig(@JsonProperty("host") final String host,
                          @JsonProperty("port") final int port,
                          @JsonProperty("scheme") final String scheme,
                          @JsonProperty("username") final String username,
                          @JsonProperty("password") final String password,
                          @JsonProperty("useSystemPropertyValues") final Boolean useSystemPropertyValues) {
        this.host = host;
        this.port = port;
        this.scheme = scheme;
        this.username = username;
        this.password = password;
        this.useSystemPropertyValues = useSystemPropertyValues;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getScheme() {
        return scheme;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Boolean getUseSystemPropertyValues() {
        return useSystemPropertyValues;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AwsProxyConfig that = (AwsProxyConfig) o;
        return port == that.port && Objects.equals(host, that.host) && Objects.equals(scheme,
                that.scheme) && Objects.equals(username, that.username) && Objects.equals(password,
                that.password) && Objects.equals(useSystemPropertyValues, that.useSystemPropertyValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, scheme, username, password, useSystemPropertyValues);
    }

    @Override
    public String toString() {
        return "AwsProxyConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", scheme='" + scheme + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", useSystemPropertyValues=" + useSystemPropertyValues +
                '}';
    }

    public static class Builder {

        private String host;
        private int port;
        private String scheme;
        private String username;
        private String password;
        private Boolean useSystemPropertyValues;

        public Builder() {
        }

        public Builder(final AwsProxyConfig awsProxyConfig) {
            this.host = awsProxyConfig.host;
            this.port = awsProxyConfig.port;
            this.scheme = awsProxyConfig.scheme;
            this.username = awsProxyConfig.username;
            this.password = awsProxyConfig.password;
            this.useSystemPropertyValues = awsProxyConfig.useSystemPropertyValues;
        }

        public Builder host(final String host) {
            this.host = host;
            return this;
        }

        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        public Builder scheme(final String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder username(final String username) {
            this.username = username;
            return this;
        }

        public Builder password(final String password) {
            this.password = password;
            return this;
        }

        public Builder useSystemPropertyValues(final Boolean useSystemPropertyValues) {
            this.useSystemPropertyValues = useSystemPropertyValues;
            return this;
        }

        public AwsProxyConfig build() {
            return new AwsProxyConfig(
                    host,
                    port,
                    scheme,
                    username,
                    password,
                    useSystemPropertyValues);
        }
    }
}
