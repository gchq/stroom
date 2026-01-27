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
        "verifyHosts",
        "knownHosts"
})
@JsonInclude(Include.NON_NULL)
public final class SshKeySecret implements Secret {

    @JsonProperty
    private final String passphrase;
    @JsonProperty
    private final String privateKey;
    @JsonProperty
    private final boolean verifyHosts;
    @JsonProperty
    private final String knownHosts;

    @JsonCreator
    public SshKeySecret(
            @JsonProperty("passphrase") final String passphrase,
            @JsonProperty("privateKey") final String privateKey,
            @JsonProperty("verifyHosts") final Boolean verifyHosts,
            @JsonProperty("knownHosts") final String knownHosts) {
        this.passphrase = passphrase;
        this.privateKey = privateKey;
        this.verifyHosts = verifyHosts == null || verifyHosts;
        this.knownHosts = knownHosts;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public boolean isVerifyHosts() {
        return verifyHosts;
    }

    public String getKnownHosts() {
        return knownHosts;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SshKeySecret that = (SshKeySecret) o;
        return verifyHosts == that.verifyHosts && Objects.equals(passphrase,
                that.passphrase) && Objects.equals(privateKey, that.privateKey) && Objects.equals(
                knownHosts,
                that.knownHosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passphrase, privateKey, verifyHosts, knownHosts);
    }
}
