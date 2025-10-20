package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

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
        "credsExpire",
        "expires",
        "secret"
})
@JsonInclude(Include.NON_NULL)
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

    /** Whether the credentials expire */
    @JsonProperty
    private boolean credsExpire;

    /** When this credential expires */
    @JsonProperty
    private long expires;

    private static final String EMPTY_NAME = "";

    /**
     * A sort-of type for this object. Not really a Document but handy to pretend it
     * is sometimes, so it has a TYPE field.
     */
    public static final String TYPE = "Credentials";

    /**
     * Default value of the UUID. Replaced by CredentialsDao.createCredentials()
     * with a correct UUID.
     */
    private static final String DEFAULT_UUID = "";

    /**
     * Creates a new, empty credentials object with a dummy UUID.
     * This object can then be used with the CredentialsDao.createCredentials()
     * method to create an actual UUID.
     */
    public Credentials() {
        this.name = EMPTY_NAME;
        this.uuid = DEFAULT_UUID;
        this.type = CredentialsType.USERNAME_PASSWORD;
        this.credsExpire = false;
        this.expires = System.currentTimeMillis();
    }

    /**
     * Constructor.
     * @param name Name. If null then empty string is assigned.
     * @param uuid UUID. Can be null in which case UUID will be assigned the default (unusable) value.
     * @param type Type. If null then empty string is assigned.
     * @param expires Time in ms since epoch when this expires.
     */
    @JsonCreator
    public Credentials(
            @JsonProperty("name") final String name,
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("type") final CredentialsType type,
            @JsonProperty("credsExpire") final boolean credsExpire,
            @JsonProperty("expires") final long expires) {

        this.name = name == null ? EMPTY_NAME : name;
        this.uuid = uuid == null ? DEFAULT_UUID : uuid;
        this.type = type == null ? CredentialsType.USERNAME_PASSWORD : type;
        this.credsExpire = credsExpire;
        this.expires = expires;
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

    public boolean isCredsExpire() {
        return credsExpire;
    }

    public void setCredsExpire(final boolean credsExpire) {
        this.credsExpire = credsExpire;
    }

    public long getExpires() {
        return expires;
    }

    public void setExpires(final long expires) {
        this.expires = expires;
    }

    /**
     * Returns a deep copy of this object.
     */
    public Credentials copy() {
        return new Credentials(
                this.name,
                this.uuid,
                this.type,
                this.credsExpire,
                this.expires);
    }

    /**
     * Returns a deep copy of this object with a new UUID.
     */
    public Credentials copyWithUuid(final String uuid) {
        return new Credentials(
                this.name,
                uuid,
                this.type,
                this.credsExpire,
                this.expires);
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
               && credsExpire == that.credsExpire
               && expires == that.expires;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uuid, type, credsExpire, expires);
    }

    @Override
    public String toString() {
        return "Credentials{" +
               "name='" + name +
               "', uuid='" + uuid +
               "', type=" + type +
               ", credsExpire=" + credsExpire +
               ", expires=" + expires +
               '}';
    }

}
