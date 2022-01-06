package stroom.search.elastic;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class CryptoConfig extends AbstractConfig implements IsStroomConfig {

    private static final String PROP_NAME_ENCRYPTION_KEY = "secretEncryptionKey";

    private final String secretEncryptionKey;

    public CryptoConfig() {
        secretEncryptionKey = "";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public CryptoConfig(@JsonProperty(PROP_NAME_ENCRYPTION_KEY) final String secretEncryptionKey) {
        this.secretEncryptionKey = secretEncryptionKey;
    }

    @JsonProperty(PROP_NAME_ENCRYPTION_KEY)
    @JsonPropertyDescription("Key used to encrypt and decrypt secrets stored in document entities")
    public String getSecretEncryptionKey() {
        return secretEncryptionKey;
    }

    @Override
    public String toString() {
        return "CryptoConfig{" +
                "secretEncryptionKey=(redacted)" +
                '}';
    }
}
