package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents the secret part of credentials.
 */
@JsonPropertyOrder({
        "passphrase",
        "privateKey",
        "publicKey"
})
@JsonInclude(Include.NON_NULL)
public final class KeyPairSecret implements Secret {

    @JsonProperty
    private final String passphrase;
    @JsonProperty
    private final String privateKey;
    @JsonProperty
    private final String publicKey;

    @JsonCreator
    public KeyPairSecret(
            @JsonProperty("passphrase") final String passphrase,
            @JsonProperty("privateKey") final String privateKey,
            @JsonProperty("publicKey") final String publicKey) {
        this.passphrase = passphrase;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyPairSecret that = (KeyPairSecret) o;
        return Objects.equals(passphrase, that.passphrase)
               && Objects.equals(privateKey, that.privateKey)
               && Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                passphrase,
                privateKey,
                publicKey);
    }
}
