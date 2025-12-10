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
public class AwsHttpConfig {

    /**
     * The amount of time to wait when initially establishing a connection before giving up and timing out.
     *
     * @param connectionTimeout timeout
     * @return The builder of the method chaining.
     */
    @JsonProperty
    private final String connectionTimeout;

    /**
     * <p>
     * Option to disable SSL cert validation and SSL host name verification.
     * This turns off x.509 validation.
     * By default, this option is off.
     * Only enable this option for testing purposes.
     *
     * @param trustAllCertificatesEnabled True if SSL cert validation is disabled.
     * @return The builder of the method chaining.
     */
    @JsonProperty
    private final Boolean trustAllCertificatesEnabled;

    /**
     * Sets the http proxy configuration to use for this client.
     *
     * @param proxyConfiguration The http proxy configuration to use
     * @return The builder of the method chaining.
     */
    @JsonProperty
    private final AwsProxyConfig proxyConfiguration;

//    /**
//     * Configure the health checks for all connections established by this client.
//     *
//     * <p>
//     * You can set a throughput threshold for a connection to be considered healthy. If a connection falls below this
//     * threshold ({@link S3CrtConnectionHealthConfiguration#minimumThroughputInBps() }) for the configurable amount
//     of time
//     * ({@link S3CrtConnectionHealthConfiguration#minimumThroughputTimeout()}), then the connection is considered
//     unhealthy
//     * and will be shut down.
//     *
//     * @param healthConfiguration The health checks config to use
//     * @return The builder of the method chaining.
//     */
//    Builder connectionHealthConfiguration(S3CrtConnectionHealthConfiguration healthConfiguration);


    @JsonCreator
    public AwsHttpConfig(@JsonProperty("connectionTimeout") final String connectionTimeout,
                         @JsonProperty("trustAllCertificatesEnabled") final Boolean trustAllCertificatesEnabled,
                         @JsonProperty("proxyConfiguration") final AwsProxyConfig proxyConfiguration) {
        this.connectionTimeout = connectionTimeout;
        this.trustAllCertificatesEnabled = trustAllCertificatesEnabled;
        this.proxyConfiguration = proxyConfiguration;
    }

    public String getConnectionTimeout() {
        return connectionTimeout;
    }

    public Boolean getTrustAllCertificatesEnabled() {
        return trustAllCertificatesEnabled;
    }

    public AwsProxyConfig getProxyConfiguration() {
        return proxyConfiguration;
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
        final AwsHttpConfig that = (AwsHttpConfig) o;
        return Objects.equals(connectionTimeout, that.connectionTimeout) && Objects.equals(
                trustAllCertificatesEnabled,
                that.trustAllCertificatesEnabled) && Objects.equals(proxyConfiguration, that.proxyConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionTimeout, trustAllCertificatesEnabled, proxyConfiguration);
    }

    @Override
    public String toString() {
        return "AwsHttpConfig{" +
                "connectionTimeout='" + connectionTimeout + '\'' +
                ", trustAllCertificatesEnabled=" + trustAllCertificatesEnabled +
                ", proxyConfiguration=" + proxyConfiguration +
                '}';
    }

    public static class Builder {

        private String connectionTimeout;
        private Boolean trustAllCertificatesEnabled;
        private AwsProxyConfig proxyConfiguration;

        public Builder() {
        }

        public Builder(final AwsHttpConfig awsHttpConfig) {
            this.connectionTimeout = awsHttpConfig.connectionTimeout;
            this.trustAllCertificatesEnabled = awsHttpConfig.trustAllCertificatesEnabled;
            this.proxyConfiguration = awsHttpConfig.proxyConfiguration;
        }

        public Builder connectionTimeout(final String connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder trustAllCertificatesEnabled(final Boolean trustAllCertificatesEnabled) {
            this.trustAllCertificatesEnabled = trustAllCertificatesEnabled;
            return this;
        }

        public Builder proxyConfiguration(final AwsProxyConfig proxyConfiguration) {
            this.proxyConfiguration = proxyConfiguration;
            return this;
        }

        public AwsHttpConfig build() {
            return new AwsHttpConfig(
                    connectionTimeout,
                    trustAllCertificatesEnabled,
                    proxyConfiguration);
        }
    }
}
