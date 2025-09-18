package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Credentials as stored by the Credentials storage system.
 * Initial use of this is in GitRepo.
 */
@JsonPropertyOrder({
        "name",
        "uuid",
        "type",
        "username",
        "password",
        "passphrase",
        "privateCert"
})
public class Credentials {

    /** The name of these credentials, set by and to display to the user */
    @JsonProperty
    private String name;

    /** The UUID of these credentials - used internally to refer to the credentials */
    @JsonProperty
    private String uuid;

    /** The type of credentials in use */
    @JsonProperty
    private CredentialsType type;

    /** Username */
    @JsonProperty
    private String username;

    /** Password or access token */
    @JsonProperty
    private String password;

    /** Passphrase for private certificate */
    @JsonProperty
    private String passphrase;

    /** Private certificate */
    @JsonProperty
    private String privateCert;

    @JsonCreator
    public Credentials(
            @JsonProperty("name") final String name,
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("type") final CredentialsType type,
            @JsonProperty("username") final String username,
            @JsonProperty("password") final String password,
            @JsonProperty("passphrase") final String passphrase,
            @JsonProperty("privateCert") final String privateCert) {
        this.name = name;
        this.uuid = uuid;
        this.type = type;
        this.username = username;
        this.password = password;
        this.passphrase = passphrase;
        this.privateCert = privateCert;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public CredentialsType getType() {
        return type;
    }

    public void setType(final CredentialsType type) {
        this.type = type;
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

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(final String passphrase) {
        this.passphrase = passphrase;
    }

    public String getPrivateCert() {
        return privateCert;
    }

    public void setPrivateCert(final String privateCert) {
        this.privateCert = privateCert;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Credentials that = (Credentials) o;
        return Objects.equals(name, that.name) && Objects.equals(uuid,
                that.uuid) && type == that.type && Objects.equals(username,
                that.username) && Objects.equals(password, that.password) && Objects.equals(passphrase,
                that.passphrase) && Objects.equals(privateCert, that.privateCert);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uuid, type, username, password, passphrase, privateCert);
    }
}
