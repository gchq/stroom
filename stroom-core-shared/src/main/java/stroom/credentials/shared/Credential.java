package stroom.credentials.shared;

import stroom.ai.shared.KeyStoreType;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
        "uuid",
        "name",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "credentialType",
        "expiryTimeMs",
        "secret"
})
@JsonInclude(Include.NON_NULL)
public class Credential implements HasDisplayValue {

    /**
     * A sort-of type for this object. Not really a Document but handy to pretend it
     * is sometimes, so it has a TYPE field.
     */
    public static final String TYPE = "Credential";

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final CredentialType credentialType;
    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final Long expiryTimeMs;

    /**
     * Constructor.
     *
     * @param uuid           UUID. Can be null in which case UUID will be assigned the default (unusable) value.
     * @param name           Name. If null then empty string is assigned.
     * @param credentialType Type. If null then empty string is assigned.
     * @param expiryTimeMs   Time in ms since epoch when this expires.
     */
    @JsonCreator
    public Credential(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("credentialType") final CredentialType credentialType,
            @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
            @JsonProperty("expiryTimeMs") final Long expiryTimeMs) {
        this.uuid = uuid;
        this.name = name;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.credentialType = credentialType == null
                ? CredentialType.USERNAME_PASSWORD
                : credentialType;
        this.keyStoreType = keyStoreType;
        this.expiryTimeMs = expiryTimeMs;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public String getCreateUser() {
        return createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    public Long getExpiryTimeMs() {
        return expiryTimeMs;
    }

    public DocRef asDocRef() {
        return new DocRef(TYPE, uuid, name);
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Credential that = (Credential) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public String toString() {
        return "Credential{" +
               "uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               ", createTimeMs=" + createTimeMs +
               ", updateTimeMs=" + updateTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateUser='" + updateUser + '\'' +
               ", credentialType=" + credentialType +
               ", keyStoreType=" + keyStoreType +
               ", expiryTimeMs=" + expiryTimeMs +
               '}';
    }
}
