package stroom.ai.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SSLConfig extends AbstractConfig implements IsProxyConfig {

    protected static final String DEFAULT_SSL_PROTOCOL = "TLSv1.2";
    protected static final boolean DEFAULT_HOSTNAME_VERIFICATION_ENABLED = true;

    @JsonProperty
    private final KeyStore keyStore;
    @JsonProperty
    private final KeyStore trustStore;
    @JsonProperty
    private final boolean hostnameVerificationEnabled;
    @JsonProperty
    private final String sslProtocol;

    public SSLConfig() {
        keyStore = new KeyStore();
        trustStore = new KeyStore();
        hostnameVerificationEnabled = DEFAULT_HOSTNAME_VERIFICATION_ENABLED;
        sslProtocol = DEFAULT_SSL_PROTOCOL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SSLConfig(@JsonProperty("keyStore") final KeyStore keyStore,
                     @JsonProperty("trustStore") final KeyStore trustStore,
                     @JsonProperty("hostnameVerificationEnabled") final Boolean hostnameVerificationEnabled,
                     @JsonProperty("sslProtocol") final String sslProtocol) {

        // SSLConfig is defaulted to null on parent config objects so we need to apply defaults in the ctor
        // as the default config tree will not contain an SSLConfig to use as a reference.
        this.keyStore = keyStore;
        this.trustStore = trustStore;
        this.hostnameVerificationEnabled = NullSafe.requireNonNullElse(
                hostnameVerificationEnabled, DEFAULT_HOSTNAME_VERIFICATION_ENABLED);
        this.sslProtocol = NullSafe.requireNonNullElse(sslProtocol, DEFAULT_SSL_PROTOCOL);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public KeyStore getTrustStore() {
        return trustStore;
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
        return hostnameVerificationEnabled == sslConfig.hostnameVerificationEnabled &&
               Objects.equals(keyStore, sslConfig.keyStore) &&
               Objects.equals(trustStore, sslConfig.trustStore) &&
               Objects.equals(sslProtocol, sslConfig.sslProtocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyStore, trustStore, hostnameVerificationEnabled, sslProtocol);
    }

    @Override
    public String toString() {
        return "SSLConfig{" +
               "keyStore='" + keyStore + '\'' +
               ", trustStore='" + trustStore + '\'' +
               ", isHostnameVerificationEnabled=" + hostnameVerificationEnabled +
               ", sslProtocol='" + sslProtocol + '\'' +
               '}';
    }

    public static class Builder {

        private KeyStore keyStore = new KeyStore();
        private KeyStore trustStore = new KeyStore();
        private boolean isHostnameVerificationEnabled = DEFAULT_HOSTNAME_VERIFICATION_ENABLED;
        private String sslProtocol = DEFAULT_SSL_PROTOCOL;

        public Builder keyStore(final KeyStore keyStore) {
            this.keyStore = keyStore;
            return this;
        }

        public Builder trustStore(final KeyStore trustStore) {
            this.trustStore = trustStore;
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
                    keyStore,
                    trustStore,
                    isHostnameVerificationEnabled,
                    sslProtocol);
        }
    }
}
