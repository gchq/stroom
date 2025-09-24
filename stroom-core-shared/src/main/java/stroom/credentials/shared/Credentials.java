package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.UUID;

/**
 * Credentials as stored by the Credentials storage system.
 * This class represents the metadata about the credentials. The secrets
 * are in the 'secret' field.
 * Initial use of this is in GitRepo.
 */
@JsonPropertyOrder({
        "name",
        "uuid",
        "type",
        "expires",
        "secret"
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

    /** When this credential expires */
    private long expires;

    /** The secret stuff */
    @JsonProperty
    private CredentialsSecret secret;

    /**
     * Constructor.
     * @param name Name. Must not be null.
     * @param uuid UUID. Can be null in which case UUID will be assigned to a random value.
     * @param type Type. Must not be null.
     * @param expires Time in ms since epoch when this expires.
     * @param secret The secret stuff. Must not be null.
     */
    @JsonCreator
    public Credentials(
            @JsonProperty("name") final String name,
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("type") final CredentialsType type,
            @JsonProperty("expires") final long expires,
            @JsonProperty("secret") final CredentialsSecret secret) {

        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        Objects.requireNonNull(secret);
        this.name = name;
        this.uuid = uuid == null ? UUID.randomUUID().toString() : uuid;
        this.type = type;
        this.expires = expires;
        this.secret = secret;
    }

    /**
     * @return Never returns null.
     */
    public String getName() {
        return name;
    }

    /**
     * @param name Must not be null.
     */
    public void setName(final String name) {
        Objects.requireNonNull(name);
        this.name = name;
    }

    /**
     * @return Never returns null.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * @param uuid Must not be null.
     */
    public void setUuid(final String uuid) {
        Objects.requireNonNull(uuid);
        this.uuid = uuid;
    }

    /**
     * @return Never returns null.
     */
    public CredentialsType getType() {
        return type;
    }

    /**
     * @param type Must not be null.
     */
    public void setType(final CredentialsType type) {
        Objects.requireNonNull(type);
        this.type = type;
    }

    public long getExpires() {
        return expires;
    }

    public void setExpires(final long expires) {
        this.expires = expires;
    }

    /**
     * @return Never returns null.
     */
    public CredentialsSecret getSecret() {
        return secret;
    }

    /**
     * @param secret Must not be null.
     */
    public void setSecret(final CredentialsSecret secret) {
        Objects.requireNonNull(secret);
        this.secret = secret;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Credentials that = (Credentials) o;
        return Objects.equals(name, that.name)
               && Objects.equals(uuid, that.uuid)
               && type == that.type
               && expires == that.expires
               && secret == that.secret;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uuid, type, expires, secret);
    }

    @Override
    public String toString() {
        return "Credentials{" +
               "name='" + name +
               "', uuid='" + uuid +
               "', type=" + type +
               ", expires=" + expires +
               ", secret=" + secret +
               '}';
    }
}
