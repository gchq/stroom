package stroom.security.shared;

import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentType;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemovePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SetPermission.class, name = "SetPermission"),
        @JsonSubTypes.Type(value = RemovePermission.class, name = "RemovePermission"),
        @JsonSubTypes.Type(value = AddDocumentCreatePermission.class, name = "AddDocumentCreatePermission"),
        @JsonSubTypes.Type(value = RemoveDocumentCreatePermission.class, name = "RemoveDocumentCreatePermission"),
        @JsonSubTypes.Type(value = AddAllDocumentCreatePermissions.class, name = "AddDocumentCreatePermission"),
        @JsonSubTypes.Type(value = RemoveAllDocumentCreatePermissions.class,
                name = "RemoveAllDocumentCreatePermissions"),
        @JsonSubTypes.Type(value = AddAllPermissionsFrom.class, name = "AddAllPermissionsFrom"),
        @JsonSubTypes.Type(value = SetAllPermissionsFrom.class, name = "SetAllPermissionsFrom"),
        @JsonSubTypes.Type(value = RemoveAllPermissions.class, name = "RemoveAllPermissions"),
})
public abstract class AbstractDocumentPermissionsChange {

    @JsonInclude(Include.NON_NULL)
    public static class SetPermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final DocumentPermission permission;

        @JsonCreator
        public SetPermission(
                @JsonProperty("userRef") final UserRef userRef,
                @JsonProperty("permission") final DocumentPermission permission) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(permission, "Null permission");
            this.userRef = userRef;
            this.permission = permission;
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public DocumentPermission getPermission() {
            return permission;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class RemovePermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final DocumentPermission permission;

        @JsonCreator
        public RemovePermission(@JsonProperty("userRef") final UserRef userRef,
                                @JsonProperty("permission") final DocumentPermission permission) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(permission, "Null permission");
            this.userRef = userRef;
            this.permission = permission;
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public DocumentPermission getPermission() {
            return permission;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class AddDocumentCreatePermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final DocumentType documentType;

        @JsonCreator
        public AddDocumentCreatePermission(@JsonProperty("userRef") final UserRef userRef,
                                           @JsonProperty("documentType") final DocumentType documentType) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(documentType, "Null documentType");
            this.userRef = userRef;
            this.documentType = documentType;
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public DocumentType getDocumentType() {
            return documentType;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class RemoveDocumentCreatePermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final DocumentType documentType;

        @JsonCreator
        public RemoveDocumentCreatePermission(@JsonProperty("userRef") final UserRef userRef,
                                              @JsonProperty("documentType") final DocumentType documentType) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(documentType, "Null documentType");
            this.userRef = userRef;
            this.documentType = documentType;
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public DocumentType getDocumentType() {
            return documentType;
        }
    }


    @JsonInclude(Include.NON_NULL)
    public static class AddAllDocumentCreatePermissions extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;

        @JsonCreator
        public AddAllDocumentCreatePermissions(@JsonProperty("userRef") final UserRef userRef) {
            Objects.requireNonNull(userRef, "Null user ref");
            this.userRef = userRef;
        }

        public UserRef getUserRef() {
            return userRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class RemoveAllDocumentCreatePermissions extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;

        @JsonCreator
        public RemoveAllDocumentCreatePermissions(@JsonProperty("userRef") final UserRef userRef) {
            Objects.requireNonNull(userRef, "Null user ref");
            this.userRef = userRef;
        }

        public UserRef getUserRef() {
            return userRef;
        }
    }


    @JsonInclude(Include.NON_NULL)
    public static class AddAllPermissionsFrom extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final DocRef sourceDocRef;

        @JsonCreator
        public AddAllPermissionsFrom(@JsonProperty("sourceDocRef") final DocRef sourceDocRef) {
            Objects.requireNonNull(sourceDocRef, "Null sourceDocRef");
            this.sourceDocRef = sourceDocRef;
        }

        public DocRef getSourceDocRef() {
            return sourceDocRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class SetAllPermissionsFrom extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final DocRef sourceDocRef;

        @JsonCreator
        public SetAllPermissionsFrom(@JsonProperty("sourceDocRef") final DocRef sourceDocRef) {
            Objects.requireNonNull(sourceDocRef, "Null sourceDocRef");
            this.sourceDocRef = sourceDocRef;
        }

        public DocRef getSourceDocRef() {
            return sourceDocRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class RemoveAllPermissions extends AbstractDocumentPermissionsChange {

    }
}
