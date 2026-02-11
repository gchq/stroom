package stroom.ai.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyStore extends AbstractConfig implements IsProxyConfig {

    protected static final KeyStoreType DEFAULT_KEYSTORE_TYPE = KeyStoreType.JKS;

    @JsonProperty
    private final String keyStorePath;
    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final String keyStorePassword;


    public KeyStore() {
        keyStorePath = null;
        keyStoreType = DEFAULT_KEYSTORE_TYPE;
        keyStorePassword = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public KeyStore(@JsonProperty("keyStorePath") final String keyStorePath,
                    @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
                    @JsonProperty("keyStorePassword") final String keyStorePassword) {

        // SSLConfig is defaulted to null on parent config objects so we need to apply defaults in the ctor
        // as the default config tree will not contain an SSLConfig to use as a reference.
        this.keyStorePath = keyStorePath;
        this.keyStoreType = NullSafe.requireNonNullElse(keyStoreType, DEFAULT_KEYSTORE_TYPE);
        this.keyStorePassword = keyStorePassword;
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
    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    /**
     * The password for the keystore
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
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
        final KeyStore sslConfig = (KeyStore) o;
        return Objects.equals(keyStorePath, sslConfig.keyStorePath) &&
               Objects.equals(keyStoreType, sslConfig.keyStoreType) &&
               Objects.equals(keyStorePassword, sslConfig.keyStorePassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyStorePath,
                keyStoreType,
                keyStorePassword);
    }

    @Override
    public String toString() {
        return "SSLKeyStore{" +
               "keyStorePath='" + keyStorePath + '\'' +
               ", keyStoreType='" + keyStoreType + '\'' +
               ", keyStorePassword='" + keyStorePassword + '\'' +
               '}';
    }

    public static class Builder {

        private String keyStorePath = null;
        private KeyStoreType keyStoreType = DEFAULT_KEYSTORE_TYPE;
        private String keyStorePassword = null;

        public Builder keyStorePath(final String keyStorePath) {
            this.keyStorePath = keyStorePath;
            return this;
        }

        public Builder keyStoreType(final KeyStoreType keyStoreType) {
            this.keyStoreType = keyStoreType;
            return this;
        }

        public Builder keyStorePassword(final String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return this;
        }


        public KeyStore build() {
            return new KeyStore(
                    keyStorePath,
                    keyStoreType,
                    keyStorePassword);
        }
    }
}
