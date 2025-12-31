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
public class HttpTlsConfig {

    private static final String DEFAULT_PROTOCOL = "TLSv1.2";
    private static final boolean DEFAULT_TRUST_SELF_SIGNED_CERTIFICATES = false;
    private static final boolean DEFAULT_VERIFY_HOSTNAME = true;

    @JsonProperty
    private final String protocol;
    @JsonProperty
    private final String provider;
    @JsonProperty
    private final String keyStoreName;
    @JsonProperty
    private final String trustStoreName;
    @JsonProperty
    private final boolean trustSelfSignedCertificates;
    @JsonProperty
    private final boolean verifyHostname;
    @JsonProperty
    private final List<String> supportedProtocols;
    @JsonProperty
    private final List<String> supportedCiphers;
    @JsonProperty
    private final String certAlias;

    @SuppressWarnings("unused")
    @JsonCreator
    public HttpTlsConfig(@JsonProperty("protocol") final String protocol,
                         @JsonProperty("provider") final String provider,
                         @JsonProperty("keyStoreName") final String keyStoreName,
                         @JsonProperty("trustStoreName") final String trustStoreName,
                         @JsonProperty("trustSelfSignedCertificates") final Boolean trustSelfSignedCertificates,
                         @JsonProperty("verifyHostname") final Boolean verifyHostname,
                         @JsonProperty("supportedProtocols") final List<String> supportedProtocols,
                         @JsonProperty("supportedCiphers") final List<String> supportedCiphers,
                         @JsonProperty("certAlias") final String certAlias) {

        // HttpClientTlsConfig is defaulted to null on parent config objects so we need to apply defaults in the ctor
        // as the default config tree will not contain an HttpClientTlsConfig to use as a reference.
        this.protocol = NullSafe.requireNonNullElse(protocol, DEFAULT_PROTOCOL);
        this.provider = provider;
        this.keyStoreName = keyStoreName;
        this.trustStoreName = trustStoreName;
        this.trustSelfSignedCertificates = NullSafe.requireNonNullElse(
                trustSelfSignedCertificates, DEFAULT_TRUST_SELF_SIGNED_CERTIFICATES);
        this.verifyHostname = NullSafe.requireNonNullElse(verifyHostname, DEFAULT_VERIFY_HOSTNAME);
        this.supportedProtocols = supportedProtocols;
        this.supportedCiphers = supportedCiphers;
        this.certAlias = certAlias;
    }

    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates;
    }

    public String getKeyStoreName() {
        return keyStoreName;
    }

    public String getTrustStoreName() {
        return trustStoreName;
    }

    public boolean isVerifyHostname() {
        return verifyHostname;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getProvider() {
        return provider;
    }

    public List<String> getSupportedCiphers() {
        return supportedCiphers;
    }

    public List<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    public String getCertAlias() {
        return certAlias;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpTlsConfig that = (HttpTlsConfig) o;
        return trustSelfSignedCertificates == that.trustSelfSignedCertificates &&
               verifyHostname == that.verifyHostname &&
               Objects.equals(protocol, that.protocol) &&
               Objects.equals(provider, that.provider) &&
               Objects.equals(keyStoreName, that.keyStoreName) &&
               Objects.equals(trustStoreName, that.trustStoreName) &&
               Objects.equals(supportedProtocols, that.supportedProtocols) &&
               Objects.equals(supportedCiphers, that.supportedCiphers) &&
               Objects.equals(certAlias, that.certAlias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol,
                provider,
                keyStoreName,
                trustStoreName,
                trustSelfSignedCertificates,
                verifyHostname,
                supportedProtocols,
                supportedCiphers,
                certAlias);
    }

    @Override
    public String toString() {
        return "HttpTlsUiConfiguration{" +
               "protocol='" + protocol + '\'' +
               ", provider='" + provider + '\'' +
               ", keyStoreName='" + keyStoreName + '\'' +
               ", trustStoreName='" + trustStoreName + '\'' +
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
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<HttpTlsConfig, Builder> {

        private String protocol = DEFAULT_PROTOCOL;
        private String provider;
        private String keyStoreName;
        private String trustStoreName;
        private boolean trustSelfSignedCertificates = DEFAULT_TRUST_SELF_SIGNED_CERTIFICATES;
        private boolean verifyHostname = DEFAULT_VERIFY_HOSTNAME;
        private List<String> supportedProtocols;
        private List<String> supportedCiphers;
        private String certAlias;

        private Builder() {
        }

        private Builder(final HttpTlsConfig httpClientTlsConfig) {
            protocol = httpClientTlsConfig.protocol;
            provider = httpClientTlsConfig.provider;
            keyStoreName = httpClientTlsConfig.keyStoreName;
            trustStoreName = httpClientTlsConfig.trustStoreName;
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

        public Builder keyStoreName(final String keyStoreName) {
            this.keyStoreName = keyStoreName;
            return self();
        }

        public Builder trustStoreName(final String trustStoreName) {
            this.trustStoreName = trustStoreName;
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

        public HttpTlsConfig build() {
            return new HttpTlsConfig(
                    protocol,
                    provider,
                    keyStoreName,
                    trustStoreName,
                    trustSelfSignedCertificates,
                    verifyHostname,
                    supportedProtocols,
                    supportedCiphers,
                    certAlias);
        }
    }
}
