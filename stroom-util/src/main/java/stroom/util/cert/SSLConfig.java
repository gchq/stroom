package stroom.util.cert;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@NotInjectableConfig
public class SSLConfig extends AbstractConfig implements IsProxyConfig {

    protected static final String DEFAULT_KEYSTORE_TYPE = "JKS";
    protected static final String DEFAULT_SSL_PROTOCOL = "TLSv1.2";
    protected static final boolean DEFAULT_HOSTNAME_VERIFICATION_ENABLED = true;

    private final String keyStorePath;
    private final String keyStoreType;
    private final String keyStorePassword;

    private final String trustStorePath;
    private final String trustStoreType;
    private final String trustStorePassword;

    private final boolean isHostnameVerificationEnabled;
    private final String sslProtocol;

    public SSLConfig() {
        keyStorePath = null;
        keyStoreType = DEFAULT_KEYSTORE_TYPE;
        keyStorePassword = null;
        trustStorePath = null;
        trustStoreType = DEFAULT_KEYSTORE_TYPE;
        trustStorePassword = null;
        isHostnameVerificationEnabled = DEFAULT_HOSTNAME_VERIFICATION_ENABLED;
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
                     @JsonProperty("isHostnameVerificationEnabled") final boolean isHostnameVerificationEnabled,
                     @JsonProperty("sslProtocol") final String sslProtocol) {
        this.keyStorePath = keyStorePath;
        this.keyStoreType = keyStoreType;
        this.keyStorePassword = keyStorePassword;
        this.trustStorePath = trustStorePath;
        this.trustStoreType = trustStoreType;
        this.trustStorePassword = trustStorePassword;
        this.isHostnameVerificationEnabled = isHostnameVerificationEnabled;
        this.sslProtocol = sslProtocol;
    }

    /**
     * The path to the keystore file that will be used for client authentication during forwarding
     */
    @JsonProperty
    @ValidFilePath
    public String getKeyStorePath() {
        return keyStorePath;
    }

    /**
     * The type of the keystore, e.g. JKS
     */
    @JsonProperty
    public String getKeyStoreType() {
        return keyStoreType;
    }

    /**
     * The password for the keystore
     */
    @JsonProperty
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * The path to the truststore file that will be used for client authentication during forwarding
     */
    @JsonProperty
    @ValidFilePath
    public String getTrustStorePath() {
        return trustStorePath;
    }

    /**
     * The type of the truststore, e.g. JKS
     */
    @JsonProperty
    public String getTrustStoreType() {
        return trustStoreType;
    }

    /**
     * The password for the truststore
     */
    @JsonProperty
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    /**
     * If true default verification of the destination hostname against the server certificate will be used.
     * If false any destination hostname will be permitted.
     */
    @JsonProperty
    public boolean isHostnameVerificationEnabled() {
        return isHostnameVerificationEnabled;
    }

    /**
     * The SSL protocol to use, e.g. TLSv1.2
     */
    @JsonProperty
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
        return isHostnameVerificationEnabled == sslConfig.isHostnameVerificationEnabled && Objects.equals(
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
                isHostnameVerificationEnabled,
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
                ", isHostnameVerificationEnabled=" + isHostnameVerificationEnabled +
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
