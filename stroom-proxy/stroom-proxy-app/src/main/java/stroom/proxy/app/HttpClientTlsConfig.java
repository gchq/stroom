package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Strings;
import io.dropwizard.validation.ValidationMethod;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is essentially a copy of
 * {@link io.dropwizard.client.ssl.TlsConfiguration}
 * so that we can extend {@link AbstractConfig} and have an equals method
 * Also {@link java.io.File} has been replaced with {@link String} for consistency
 * with our other config
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class HttpClientTlsConfig extends AbstractConfig implements IsProxyConfig {

    @NotEmpty
    private final String protocol;

    @Nullable
    private final String provider;

    @Nullable
    private final String keyStorePath;

    @Nullable
    private final String keyStorePassword;

    @NotEmpty
    private final String keyStoreType;

    @Nullable
    private final String trustStorePath;

    @Nullable
    private final String trustStorePassword;

    @NotEmpty
    private final String trustStoreType;

    private final boolean trustSelfSignedCertificates;

    private final boolean verifyHostname;

    @Nullable
    private final List<String> supportedProtocols;

    @Nullable
    private final List<String> supportedCiphers;

    @Nullable
    private final String certAlias;

    public HttpClientTlsConfig() {
        protocol = "TLSv1.2";
        provider = null;
        keyStorePath = null;
        keyStorePassword = null;
        keyStoreType = "JKS";
        trustStorePath = null;
        trustStorePassword = null;
        trustStoreType = "JKS";
        trustSelfSignedCertificates = false;
        verifyHostname = true;
        supportedProtocols = null;
        supportedCiphers = null;
        certAlias = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public HttpClientTlsConfig(@JsonProperty("protocol") final String protocol,
                               @Nullable @JsonProperty("provider") final String provider,
                               @Nullable @JsonProperty("keyStorePath") final String keyStorePath,
                               @Nullable @JsonProperty("keyStorePassword") final String keyStorePassword,
                               @JsonProperty("keyStoreType") final String keyStoreType,
                               @Nullable @JsonProperty("trustStorePath") final String trustStorePath,
                               @Nullable @JsonProperty("trustStorePassword") final String trustStorePassword,
                               @JsonProperty("trustStoreType") final String trustStoreType,
                               @JsonProperty("trustSelfSignedCertificates") final boolean trustSelfSignedCertificates,
                               @JsonProperty("verifyHostname") final boolean verifyHostname,
                               @Nullable @JsonProperty("supportedProtocols") final List<String> supportedProtocols,
                               @Nullable @JsonProperty("supportedCiphers") final List<String> supportedCiphers,
                               @Nullable @JsonProperty("certAlias") final String certAlias) {
        this.protocol = protocol;
        this.provider = provider;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.keyStoreType = keyStoreType;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = trustStoreType;
        this.trustSelfSignedCertificates = trustSelfSignedCertificates;
        this.verifyHostname = verifyHostname;
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
    @Nullable
    public String getKeyStorePath() {
        return keyStorePath;
    }

    @JsonProperty
    @Nullable
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @JsonProperty
    public String getKeyStoreType() {
        return keyStoreType;
    }

    @JsonProperty
    public String getTrustStoreType() {
        return trustStoreType;
    }

    @ValidFilePath
    @JsonProperty
    @Nullable
    public String getTrustStorePath() {
        return trustStorePath;
    }

    @JsonProperty
    @Nullable
    public String getTrustStorePassword() {
        return trustStorePassword;
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
    @Nullable
    public String getProvider() {
        return provider;
    }

    @Nullable
    @JsonProperty
    public List<String> getSupportedCiphers() {
        return supportedCiphers;
    }

    @Nullable
    @JsonProperty
    public List<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    @Nullable
    @JsonProperty
    public String getCertAlias() {
        return certAlias;
    }

    @ValidationMethod(message = "keyStorePassword should not be null or empty if keyStorePath not null")
    public boolean isValidKeyStorePassword() {
        return keyStorePath == null || keyStoreType.startsWith("Windows-") || !Strings.isNullOrEmpty(keyStorePassword);
    }

    @ValidationMethod(message = "trustStorePassword should not be null or empty if trustStorePath not null")
    public boolean isValidTrustStorePassword() {
        return trustStorePath == null || trustStoreType.startsWith("Windows-") || !Strings.isNullOrEmpty(
                trustStorePassword);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String protocol;
        private String provider;
        private String keyStorePath;
        private String keyStorePassword;
        private String keyStoreType;
        private String trustStorePath;
        private String trustStorePassword;
        private String trustStoreType;
        private boolean trustSelfSignedCertificates;
        private boolean verifyHostname;
        private List<String> supportedProtocols;
        private List<String> supportedCiphers;
        private String certAlias;

        public Builder() {
            final HttpClientTlsConfig httpClientTlsConfig = new HttpClientTlsConfig();
            protocol = httpClientTlsConfig.getProtocol();
            provider = httpClientTlsConfig.getProvider();
            keyStorePath = httpClientTlsConfig.getKeyStorePath();
            keyStorePassword = httpClientTlsConfig.getKeyStorePassword();
            keyStoreType = httpClientTlsConfig.getKeyStoreType();
            trustStorePath = httpClientTlsConfig.getTrustStorePath();
            trustStorePassword = httpClientTlsConfig.getTrustStorePassword();
            trustStoreType = httpClientTlsConfig.getTrustStoreType();
            trustSelfSignedCertificates = httpClientTlsConfig.isTrustSelfSignedCertificates();
            verifyHostname = httpClientTlsConfig.isVerifyHostname();
            supportedProtocols = httpClientTlsConfig.getSupportedProtocols();
            supportedCiphers = httpClientTlsConfig.getSupportedCiphers();
            certAlias = httpClientTlsConfig.getCertAlias();
        }

        public Builder withProtocol(final String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder withProvider(final String provider) {
            this.provider = provider;
            return this;
        }

        public Builder withKeyStorePath(final String keyStorePath) {
            this.keyStorePath = keyStorePath;
            return this;
        }

        public Builder withKeyStorePassword(final String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return this;
        }

        public Builder withKeyStoreType(final String keyStoreType) {
            this.keyStoreType = keyStoreType;
            return this;
        }

        public Builder withTrustStorePath(final String trustStorePath) {
            this.trustStorePath = trustStorePath;
            return this;
        }

        public Builder withTrustStorePassword(final String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder withTrustStoreType(final String trustStoreType) {
            this.trustStoreType = trustStoreType;
            return this;
        }

        public Builder withTrustSelfSignedCertificates(final boolean trustSelfSignedCertificates) {
            this.trustSelfSignedCertificates = trustSelfSignedCertificates;
            return this;
        }

        public Builder withVerifyHostname(final boolean verifyHostname) {
            this.verifyHostname = verifyHostname;
            return this;
        }

        public Builder withSupportedProtocols(final List<String> supportedProtocols) {
            this.supportedProtocols = supportedProtocols;
            return this;
        }

        public Builder withSupportedCiphers(final List<String> supportedCiphers) {
            this.supportedCiphers = supportedCiphers;
            return this;
        }

        public Builder withCertAlias(final String certAlias) {
            this.certAlias = certAlias;
            return this;
        }

        public HttpClientTlsConfig build() {
            return new HttpClientTlsConfig(
                    protocol,
                    provider,
                    keyStorePath,
                    keyStorePassword,
                    keyStoreType,
                    trustStorePath,
                    trustStorePassword,
                    trustStoreType,
                    trustSelfSignedCertificates,
                    verifyHostname,
                    supportedProtocols,
                    supportedCiphers,
                    certAlias);
        }
    }
}
