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

package stroom.util.cert;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SSLConfig extends AbstractConfig implements IsProxyConfig {

    protected static final String DEFAULT_KEYSTORE_TYPE = "JKS";
    protected static final String DEFAULT_SSL_PROTOCOL = "TLSv1.2";
    protected static final boolean DEFAULT_HOSTNAME_VERIFICATION_ENABLED = true;

    @JsonProperty
    private final String keyStorePath;
    @JsonProperty
    private final String keyStoreType;
    @JsonProperty
    private final String keyStorePassword;

    @JsonProperty
    private final String trustStorePath;
    @JsonProperty
    private final String trustStoreType;
    @JsonProperty
    private final String trustStorePassword;

    @JsonProperty
    private final boolean hostnameVerificationEnabled;
    @JsonProperty
    private final String sslProtocol;

    public SSLConfig() {
        keyStorePath = null;
        keyStoreType = DEFAULT_KEYSTORE_TYPE;
        keyStorePassword = null;
        trustStorePath = null;
        trustStoreType = DEFAULT_KEYSTORE_TYPE;
        trustStorePassword = null;
        hostnameVerificationEnabled = DEFAULT_HOSTNAME_VERIFICATION_ENABLED;
        sslProtocol = DEFAULT_SSL_PROTOCOL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SSLConfig(@JsonProperty("keyStorePath") final String keyStorePath,
                     @JsonProperty("keyStoreType") final String keyStoreType,
                     @JsonProperty("keyStorePassword") final String keyStorePassword,
                     @JsonProperty("trustStorePath") final String trustStorePath,
                     @JsonProperty("trustStoreType") final String trustStoreType,
                     @JsonProperty("trustStorePassword") final String trustStorePassword,
                     @JsonProperty("hostnameVerificationEnabled") final Boolean hostnameVerificationEnabled,
                     @JsonProperty("sslProtocol") final String sslProtocol) {

        // SSLConfig is defaulted to null on parent config objects so we need to apply defaults in the ctor
        // as the default config tree will not contain an SSLConfig to use as a reference.
        this.keyStorePath = keyStorePath;
        this.keyStoreType = Objects.requireNonNullElse(keyStoreType, DEFAULT_KEYSTORE_TYPE);
        this.keyStorePassword = keyStorePassword;
        this.trustStorePath = trustStorePath;
        this.trustStoreType = Objects.requireNonNullElse(trustStoreType, DEFAULT_KEYSTORE_TYPE);
        this.trustStorePassword = trustStorePassword;
        this.hostnameVerificationEnabled = Objects.requireNonNullElse(
                hostnameVerificationEnabled, DEFAULT_HOSTNAME_VERIFICATION_ENABLED);
        this.sslProtocol = Objects.requireNonNullElse(sslProtocol, DEFAULT_SSL_PROTOCOL);
    }

    /**
     * The path to the keystore file that will be used for client authentication during forwarding
     */
    @ValidFilePath
    public String getKeyStorePath() {
        return keyStorePath;
    }

    /**
     * The type of the keystore, e.g. JKS
     */
    public String getKeyStoreType() {
        return keyStoreType;
    }

    /**
     * The password for the keystore
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * The path to the truststore file that will be used for client authentication during forwarding
     */
    @ValidFilePath
    public String getTrustStorePath() {
        return trustStorePath;
    }

    /**
     * The type of the truststore, e.g. JKS
     */
    public String getTrustStoreType() {
        return trustStoreType;
    }

    /**
     * The password for the truststore
     */
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    /**
     * If true default verification of the destination hostname against the server certificate will be used.
     * If false any destination hostname will be permitted.
     */
    public boolean isHostnameVerificationEnabled() {
        return hostnameVerificationEnabled;
    }

    /**
     * The SSL protocol to use, e.g. TLSv1.2
     */
    public String getSslProtocol() {
        return sslProtocol;
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
        final SSLConfig sslConfig = (SSLConfig) o;
        return hostnameVerificationEnabled == sslConfig.hostnameVerificationEnabled && Objects.equals(
                keyStorePath,
                sslConfig.keyStorePath) && Objects.equals(keyStoreType,
                sslConfig.keyStoreType) && Objects.equals(keyStorePassword,
                sslConfig.keyStorePassword) && Objects.equals(trustStorePath,
                sslConfig.trustStorePath) && Objects.equals(trustStoreType,
                sslConfig.trustStoreType) && Objects.equals(trustStorePassword,
                sslConfig.trustStorePassword) && Objects.equals(sslProtocol, sslConfig.sslProtocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyStorePath,
                keyStoreType,
                keyStorePassword,
                trustStorePath,
                trustStoreType,
                trustStorePassword,
                hostnameVerificationEnabled,
                sslProtocol);
    }

    @Override
    public String toString() {
        return "SSLConfig{" +
               "keyStorePath='" + keyStorePath + '\'' +
               ", keyStoreType='" + keyStoreType + '\'' +
               ", keyStorePassword='" + keyStorePassword + '\'' +
               ", trustStorePath='" + trustStorePath + '\'' +
               ", trustStoreType='" + trustStoreType + '\'' +
               ", trustStorePassword='" + trustStorePassword + '\'' +
               ", isHostnameVerificationEnabled=" + hostnameVerificationEnabled +
               ", sslProtocol='" + sslProtocol + '\'' +
               '}';
    }

    public static class Builder {

        private String keyStorePath = null;
        private String keyStoreType = DEFAULT_KEYSTORE_TYPE;
        private String keyStorePassword = null;
        private String trustStorePath = null;
        private String trustStoreType = DEFAULT_KEYSTORE_TYPE;
        private String trustStorePassword = null;
        private boolean isHostnameVerificationEnabled = DEFAULT_HOSTNAME_VERIFICATION_ENABLED;
        private String sslProtocol = DEFAULT_SSL_PROTOCOL;

        public Builder withKeyStorePath(final String keyStorePath) {
            this.keyStorePath = keyStorePath;
            return this;
        }

        public Builder withKeyStoreType(final String keyStoreType) {
            this.keyStoreType = keyStoreType;
            return this;
        }

        public Builder withKeyStorePassword(final String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return this;
        }

        public Builder withTrustStorePath(final String trustStorePath) {
            this.trustStorePath = trustStorePath;
            return this;
        }

        public Builder withTrustStoreType(final String trustStoreType) {
            this.trustStoreType = trustStoreType;
            return this;
        }

        public Builder withTrustStorePassword(final String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder withHostnameVerificationEnabled(final boolean hostnameVerificationEnabled) {
            isHostnameVerificationEnabled = hostnameVerificationEnabled;
            return this;
        }

        public Builder withSslProtocol(final String sslProtocol) {
            this.sslProtocol = sslProtocol;
            return this;
        }

        public String getSslProtocol() {
            return sslProtocol;
        }

        public SSLConfig build() {
            return new SSLConfig(
                    keyStorePath,
                    keyStoreType,
                    keyStorePassword,
                    trustStorePath,
                    trustStoreType,
                    trustStorePassword,
                    isHostnameVerificationEnabled,
                    sslProtocol);
        }
    }
}
