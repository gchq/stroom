package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Class to wrap Credentials with enough permission info
 * to help the UI.
 */
@JsonPropertyOrder({
        "credential",
        "edit",
        "delete"
})
@JsonInclude(Include.NON_NULL)
public class CredentialWithPerms {

    @JsonProperty
    private final Credential credential;

    /**
     * Edit permission
     */
    @JsonProperty
    private final boolean edit;

    /**
     * Delete permission
     */
    @JsonProperty
    private final boolean delete;

    /**
     * Used when we want credentials with all permissions.
     *
     * @param credential The credentials to store. Must not be null.
     */
    public CredentialWithPerms(final Credential credential) {
        this(credential, true, true);
    }

    /**
     * Constructor.
     *
     * @param credential Must not be null.
     * @param edit       If the user has edit permission.
     * @param delete     If the user has delete permission.
     */
    @JsonCreator
    public CredentialWithPerms(
            @JsonProperty("credential") final Credential credential,
            @JsonProperty("edit") final boolean edit,
            @JsonProperty("delete") final boolean delete) {
        Objects.requireNonNull(credential);
        this.credential = credential;
        this.edit = edit;
        this.delete = delete;
    }

    /**
     * @return Never returns null.
     */
    public Credential getCredential() {
        return credential;
    }

    public boolean isEdit() {
        return edit;
    }

    public boolean isDelete() {
        return delete;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialWithPerms that = (CredentialWithPerms) o;
        return Objects.equals(credential, that.credential);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(credential);
    }

    @Override
    public String toString() {
        return "CredentialsWithPerms{" +
               "credentials=" + credential +
               ", edit=" + edit +
               ", delete=" + delete +
               '}';
    }
}
