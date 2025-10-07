package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents the secret part of credentials.
 * All fields can be null.
 */
@JsonPropertyOrder({
        "username",
        "password",
        "accessToken",
        "passphrase",
        "privateKey",
        "serverPublicKey"
})
@JsonInclude(Include.NON_NULL)
public class CredentialsSecret {

    /** Username */
    @JsonProperty
    private String username;

    /** Password */
    @JsonProperty
    private String password;

    /** Access token */
    @JsonProperty
    private String accessToken;

    /** Passphrase for private certificate */
    @JsonProperty
    private String passphrase;

    /** Private certificate */
    @JsonProperty
    private String privateKey;

    /** Server public certificate */
    @JsonProperty
    private String serverPublicKey;

    /**
     * Default constructor. All elements are null.
     */
    public CredentialsSecret() {
        // No code
    }

    @JsonCreator
    public CredentialsSecret(
            @JsonProperty("username") final String username,
            @JsonProperty("password") final String password,
            @JsonProperty("accessToken") final String accessToken,
            @JsonProperty("passphrase") final String passphrase,
            @JsonProperty("privateKey") final String privateKey,
            @JsonProperty("serverPublicKey") final String serverPublicKey) {

        this.username = username;
        this.password = password;
        this.accessToken = accessToken;
        this.passphrase = passphrase;
        this.privateKey = privateKey;
        this.serverPublicKey = serverPublicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(final String passphrase) {
        this.passphrase = passphrase;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(final String privateKey) {
        this.privateKey = privateKey;
    }

    public String getServerPublicKey() {
        return serverPublicKey;
    }

    public void setServerPublicKey(final String serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }

    /**
     * @return A deep copy of this object.
     */
    public CredentialsSecret copy() {
        return new CredentialsSecret(this.username,
                this.password,
                this.accessToken,
                this.passphrase,
                this.privateKey,
                this.serverPublicKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialsSecret that = (CredentialsSecret) o;
        return Objects.equals(username, that.username)
               && Objects.equals(password, that.password)
               && Objects.equals(accessToken, that.accessToken)
               && Objects.equals(passphrase, that.passphrase)
               && Objects.equals(privateKey, that.privateKey)
               && Objects.equals(serverPublicKey, that.serverPublicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username,
                password,
                accessToken,
                passphrase,
                privateKey,
                serverPublicKey);
    }

    @Override
    public String toString() {
        return "CredentialsSecret { ****** }";
    }
}
