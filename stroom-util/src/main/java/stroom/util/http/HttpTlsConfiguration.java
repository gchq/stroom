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
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Strings;

import java.util.List;
import java.util.Objects;

/**
 * This class is essentially a copy of
 * {@link io.dropwizard.client.ssl.TlsConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method
 * Also {@link java.io.File} has been replaced with {@link String} for consistency
 * with our other config
 * Values are extracted from this using reflection by {@link HttpClientConfigConverter} so it is
 * key that the method names match.
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class HttpTlsConfiguration extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    protected static final String DEFAULT_PROTOCOL = "TLSv1.2";
    protected static final String DEFAULT_KEY_STORE_TYPE = "JKS";
    protected static final boolean DEFAULT_TRUST_SELF_SIGNED_CERTIFICATES = false;
    protected static final boolean DEFAULT_VERIFY_HOSTNAME = true;

    private final String protocol;

    private final String provider;

    private final String keyStorePath;

    private final String keyStorePassword;

    private final String keyStoreType;

    private final String keyStoreProvider;

    private final String trustStorePath;

    private final String trustStorePassword;

    private final String trustStoreType;

    private final String trustStoreProvider;

    private final boolean trustSelfSignedCertificates;

    private final boolean verifyHostname;

    private final List<String> supportedProtocols;

    private final List<String> supportedCiphers;

    private final String certAlias;

    public HttpTlsConfiguration() {
        protocol = DEFAULT_PROTOCOL;
        provider = null;
        keyStorePath = null;
        keyStorePassword = null;
        keyStoreType = DEFAULT_KEY_STORE_TYPE;
        keyStoreProvider = null;
        trustStorePath = null;
        trustStorePassword = null;
        trustStoreType = DEFAULT_KEY_STORE_TYPE;
        trustSelfSignedCertificates = DEFAULT_TRUST_SELF_SIGNED_CERTIFICATES;
        trustStoreProvider = null;
        verifyHostname = DEFAULT_VERIFY_HOSTNAME;
        supportedProtocols = null;
        supportedCiphers = null;
        certAlias = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public HttpTlsConfiguration(@JsonProperty("protocol") final String protocol,
                                @JsonProperty("provider") final String provider,
                                @JsonProperty("keyStorePath") final String keyStorePath,
                                @JsonProperty("keyStorePassword") final String keyStorePassword,
                                @JsonProperty("keyStoreType") final String keyStoreType,
                                @JsonProperty("keyStoreProvider") final String keyStoreProvider,
                                @JsonProperty("trustStorePath") final String trustStorePath,
                                @JsonProperty("trustStorePassword") final String trustStorePassword,
                                @JsonProperty("trustStoreType") final String trustStoreType,
                                @JsonProperty("trustStoreProvider") final String trustStoreProvider,
                                @JsonProperty("trustSelfSignedCertificates") final Boolean trustSelfSignedCertificates,
                                @JsonProperty("verifyHostname") final Boolean verifyHostname,
                                @JsonProperty("supportedProtocols") final List<String> supportedProtocols,
                                @JsonProperty("supportedCiphers") final List<String> supportedCiphers,
                                @JsonProperty("certAlias") final String certAlias) {

        // HttpClientTlsConfig is defaulted to null on parent config objects so we need to apply defaults in the ctor
        // as the default config tree will not contain an HttpClientTlsConfig to use as a reference.
        this.protocol = Objects.requireNonNullElse(protocol, DEFAULT_PROTOCOL);
        this.provider = provider;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.keyStoreType = Objects.requireNonNullElse(keyStoreType, DEFAULT_KEY_STORE_TYPE);
        this.keyStoreProvider = keyStoreProvider;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = Objects.requireNonNullElse(trustStoreType, DEFAULT_KEY_STORE_TYPE);
        this.trustStoreProvider = trustStoreProvider;
        this.trustSelfSignedCertificates = Objects.requireNonNullElse(
                trustSelfSignedCertificates, DEFAULT_TRUST_SELF_SIGNED_CERTIFICATES);
        this.verifyHostname = Objects.requireNonNullElse(verifyHostname, DEFAULT_VERIFY_HOSTNAME);
        this.supportedProtocols = supportedProtocols;
        this.supportedCiphers = supportedCiphers;
        this.certAlias = certAlias;
    }

    @JsonProperty
    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates;
    }

    @ValidFilePath
    @JsonProperty
    public String getKeyStorePath() {
        return keyStorePath;
    }

    @JsonProperty
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @JsonProperty
    public String getKeyStoreType() {
        return keyStoreType;
    }

    @JsonProperty
    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    @ValidFilePath
    @JsonProperty
    public String getTrustStorePath() {
        return trustStorePath;
    }

    @JsonProperty
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    @JsonProperty
    public String getTrustStoreType() {
        return trustStoreType;
    }

    @JsonProperty
    public String getTrustStoreProvider() {
        return trustStoreProvider;
    }

    @JsonProperty
    public boolean isVerifyHostname() {
        return verifyHostname;
    }

    @JsonProperty
    public String getProtocol() {
        return protocol;
    }

    @JsonProperty
    public String getProvider() {
        return provider;
    }

    @JsonProperty
    public List<String> getSupportedCiphers() {
        return supportedCiphers;
    }

    @JsonProperty
    public List<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    @JsonProperty
    public String getCertAlias() {
        return certAlias;
    }

    @SuppressWarnings("unused") // Used by javax.validation
    @JsonIgnore
    public boolean isValidKeyStorePassword() {
        return keyStorePath == null
               || keyStoreType.startsWith("Windows-")
               || !Strings.isNullOrEmpty(keyStorePassword);
    }

    @SuppressWarnings("unused") // Used by javax.validation
    @JsonIgnore
    public boolean isValidTrustStorePassword() {
        return trustStorePath == null
               || trustStoreType.startsWith("Windows-")
               || !Strings.isNullOrEmpty(trustStorePassword);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpTlsConfiguration that = (HttpTlsConfiguration) o;
        return trustSelfSignedCertificates == that.trustSelfSignedCertificates &&
               verifyHostname == that.verifyHostname &&
               Objects.equals(protocol, that.protocol) &&
               Objects.equals(provider, that.provider) &&
               Objects.equals(keyStorePath, that.keyStorePath) &&
               Objects.equals(keyStorePassword, that.keyStorePassword) &&
               Objects.equals(keyStoreType, that.keyStoreType) &&
               Objects.equals(keyStoreProvider, that.keyStoreProvider) &&
               Objects.equals(trustStorePath, that.trustStorePath) &&
               Objects.equals(trustStorePassword, that.trustStorePassword) &&
               Objects.equals(trustStoreType, that.trustStoreType) &&
               Objects.equals(trustStoreProvider, that.trustStoreProvider) &&
               Objects.equals(supportedProtocols, that.supportedProtocols) &&
               Objects.equals(supportedCiphers, that.supportedCiphers) &&
               Objects.equals(certAlias, that.certAlias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol,
                provider,
                keyStorePath,
                keyStorePassword,
                keyStoreType,
                keyStoreProvider,
                trustStorePath,
                trustStorePassword,
                trustStoreType,
                trustStoreProvider,
                trustSelfSignedCertificates,
                verifyHostname,
                supportedProtocols,
                supportedCiphers,
                certAlias);
    }

    @Override
    public String toString() {
        return "HttpTlsConfiguration{" +
               "protocol='" + protocol + '\'' +
               ", provider='" + provider + '\'' +
               ", keyStorePath='" + keyStorePath + '\'' +
               ", keyStorePassword='" + keyStorePassword + '\'' +
               ", keyStoreType='" + keyStoreType + '\'' +
               ", keyStoreProvider='" + keyStoreProvider + '\'' +
               ", trustStorePath='" + trustStorePath + '\'' +
               ", trustStorePassword='" + trustStorePassword + '\'' +
               ", trustStoreType='" + trustStoreType + '\'' +
               ", trustStoreProvider='" + trustStoreProvider + '\'' +
               ", trustSelfSignedCertificates=" + trustSelfSignedCertificates +
               ", verifyHostname=" + verifyHostname +
               ", supportedProtocols=" + supportedProtocols +
               ", supportedCiphers=" + supportedCiphers +
               ", certAlias='" + certAlias + '\'' +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder(new HttpTlsConfiguration());
    }

    public static class Builder extends AbstractBuilder<HttpTlsConfiguration, HttpTlsConfiguration.Builder> {

        private String protocol;
        private String provider;
        private String keyStorePath;
        private String keyStorePassword;
        private String keyStoreType;
        private String keyStoreProvider;
        private String trustStorePath;
        private String trustStorePassword;
        private String trustStoreType;
        private String trustStoreProvider;
        private boolean trustSelfSignedCertificates;
        private boolean verifyHostname;
        private List<String> supportedProtocols;
        private List<String> supportedCiphers;
        private String certAlias;

        public Builder() {
            this(new HttpTlsConfiguration());
        }

        public Builder(final HttpTlsConfiguration httpClientTlsConfig) {
            protocol = httpClientTlsConfig.protocol;
            provider = httpClientTlsConfig.provider;
            keyStorePath = httpClientTlsConfig.keyStorePath;
            keyStorePassword = httpClientTlsConfig.keyStorePassword;
            keyStoreType = httpClientTlsConfig.keyStoreType;
            keyStoreProvider = httpClientTlsConfig.keyStoreProvider;
            trustStorePath = httpClientTlsConfig.trustStorePath;
            trustStorePassword = httpClientTlsConfig.trustStorePassword;
            trustStoreType = httpClientTlsConfig.trustStoreType;
            trustStoreProvider = httpClientTlsConfig.trustStoreProvider;
            trustSelfSignedCertificates = httpClientTlsConfig.trustSelfSignedCertificates;
            verifyHostname = httpClientTlsConfig.verifyHostname;
            supportedProtocols = httpClientTlsConfig.supportedProtocols;
            supportedCiphers = httpClientTlsConfig.supportedCiphers;
            certAlias = httpClientTlsConfig.certAlias;
        }

        public Builder protocol(final String protocol) {
            this.protocol = protocol;
            return self();
        }

        public Builder provider(final String provider) {
            this.provider = provider;
            return self();
        }

        public Builder keyStorePath(final String keyStorePath) {
            this.keyStorePath = keyStorePath;
            return self();
        }

        public Builder keyStorePassword(final String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return self();
        }

        public Builder keyStoreType(final String keyStoreType) {
            this.keyStoreType = keyStoreType;
            return self();
        }

        public Builder keyStoreProvider(final String keyStoreProvider) {
            this.keyStoreProvider = keyStoreProvider;
            return self();
        }

        public Builder trustStorePath(final String trustStorePath) {
            this.trustStorePath = trustStorePath;
            return self();
        }

        public Builder trustStorePassword(final String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return self();
        }

        public Builder trustStoreType(final String trustStoreType) {
            this.trustStoreType = trustStoreType;
            return self();
        }

        public Builder trustStoreProvider(final String trustStoreProvider) {
            this.trustStoreProvider = trustStoreProvider;
            return self();
        }

        public Builder trustSelfSignedCertificates(final boolean trustSelfSignedCertificates) {
            this.trustSelfSignedCertificates = trustSelfSignedCertificates;
            return self();
        }

        public Builder verifyHostname(final boolean verifyHostname) {
            this.verifyHostname = verifyHostname;
            return self();
        }

        public Builder supportedProtocols(final List<String> supportedProtocols) {
            this.supportedProtocols = supportedProtocols;
            return self();
        }

        public Builder supportedCiphers(final List<String> supportedCiphers) {
            this.supportedCiphers = supportedCiphers;
            return self();
        }

        public Builder certAlias(final String certAlias) {
            this.certAlias = certAlias;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public HttpTlsConfiguration build() {
            return new HttpTlsConfiguration(
                    protocol,
                    provider,
                    keyStorePath,
                    keyStorePassword,
                    keyStoreType,
                    keyStoreProvider,
                    trustStorePath,
                    trustStorePassword,
                    trustStoreType,
                    trustStoreProvider,
                    trustSelfSignedCertificates,
                    verifyHostname,
                    supportedProtocols,
                    supportedCiphers,
                    certAlias);
        }
    }
}
