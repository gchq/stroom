package stroom.security.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class DocumentUserPermissions {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final DocumentPermission permission;
    @JsonProperty
    private final Set<String> documentCreatePermissions;

    @JsonCreator
    public DocumentUserPermissions(@JsonProperty("userRef") final UserRef userRef,
                                   @JsonProperty("permission") final DocumentPermission permission,
                                   @JsonProperty("documentCreatePermissions") final Set<String>
                                           documentCreatePermissions) {
        this.userRef = userRef;
        this.permission = permission;
        this.documentCreatePermissions = documentCreatePermissions;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public DocumentPermission getPermission() {
        return permission;
    }

    public Set<String> getDocumentCreatePermissions() {
        return documentCreatePermissions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocumentUserPermissions that = (DocumentUserPermissions) o;
        return Objects.equals(userRef, that.userRef);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userRef);
    }
}
