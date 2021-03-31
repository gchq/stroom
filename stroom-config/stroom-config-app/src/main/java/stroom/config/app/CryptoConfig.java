package stroom.config.app;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class CryptoConfig extends AbstractConfig {

    private static final String PROP_NAME_ENCRYPTION_KEY = "secretEncryptionKey";

    private String secretEncryptionKey = "";

    public CryptoConfig() { }

    @JsonProperty(PROP_NAME_ENCRYPTION_KEY)
    @JsonPropertyDescription("Key used to encrypt and decrypt secrets stored in document entities")
    public String getSecretEncryptionKey() {
        return secretEncryptionKey;
    }

    public void setSecretEncryptionKey(final String secretEncryptionKey) {
        this.secretEncryptionKey = secretEncryptionKey;
    }

    @Override
    public String toString() {
        return "CryptoConfig{" +
                "secretEncryptionKey=(redacted)" +
                '}';
    }
}
