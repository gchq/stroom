/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.shared;

import stroom.docref.DocRef;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemovePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.util.shared.SerialisationTestConstructor;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SetPermission.class, name = "SetPermission"),
        @JsonSubTypes.Type(value = RemovePermission.class, name = "RemovePermission"),
        @JsonSubTypes.Type(value = AddDocumentUserCreatePermission.class,
                name = "AddDocumentUserCreatePermission"),
        @JsonSubTypes.Type(value = RemoveDocumentUserCreatePermission.class,
                name = "RemoveDocumentUserCreatePermission"),
        @JsonSubTypes.Type(value = SetDocumentUserCreatePermissions.class,
                name = "SetDocumentUserCreatePermissions"),
        @JsonSubTypes.Type(value = AddAllDocumentUserCreatePermissions.class,
                name = "AddAllDocumentUserCreatePermissions"),
        @JsonSubTypes.Type(value = RemoveAllDocumentUserCreatePermissions.class,
                name = "RemoveAllDocumentUserCreatePermissions"),
        @JsonSubTypes.Type(value = AddAllPermissionsFrom.class, name = "AddAllPermissionsFrom"),
        @JsonSubTypes.Type(value = SetAllPermissionsFrom.class, name = "SetAllPermissionsFrom"),
        @JsonSubTypes.Type(value = RemoveAllPermissions.class, name = "RemoveAllPermissions"),
})
public abstract sealed class AbstractDocumentPermissionsChange permits
        SetPermission,
        RemovePermission,
        AddDocumentUserCreatePermission,
        RemoveDocumentUserCreatePermission,
        SetDocumentUserCreatePermissions,
        AddAllDocumentUserCreatePermissions,
        RemoveAllDocumentUserCreatePermissions,
        AddAllPermissionsFrom,
        SetAllPermissionsFrom,
        RemoveAllPermissions {

    @JsonInclude(Include.NON_NULL)
    public static final class SetPermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final DocumentPermission permission;

        @JsonCreator
        public SetPermission(
                @JsonProperty("userRef") final UserRef userRef,
                @JsonProperty("permission") final DocumentPermission permission) {
            Objects.requireNonNull(userRef, "Null user ref");
            this.userRef = userRef;
            this.permission = permission;
        }

        @SerialisationTestConstructor
        private SetPermission() {
            this(UserRef.builder().build(), DocumentPermission.VIEW);
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public DocumentPermission getPermission() {
            return permission;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class RemovePermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;

        @JsonCreator
        public RemovePermission(
                @JsonProperty("userRef") final UserRef userRef) {
            Objects.requireNonNull(userRef, "Null user ref");
            this.userRef = userRef;
        }

        @SerialisationTestConstructor
        private RemovePermission() {
            this(UserRef.builder().build());
        }

        public UserRef getUserRef() {
            return userRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class AddDocumentUserCreatePermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final String documentType;

        @JsonCreator
        public AddDocumentUserCreatePermission(@JsonProperty("userRef") final UserRef userRef,
                                               @JsonProperty("documentType") final String documentType) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(documentType, "Null documentType");
            this.userRef = userRef;
            this.documentType = documentType;
        }

        @SerialisationTestConstructor
        private AddDocumentUserCreatePermission() {
            this(UserRef.builder().build(), "test");
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public String getDocumentType() {
            return documentType;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class RemoveDocumentUserCreatePermission extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final String documentType;

        @JsonCreator
        public RemoveDocumentUserCreatePermission(@JsonProperty("userRef") final UserRef userRef,
                                                  @JsonProperty("documentType") final String documentType) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(documentType, "Null documentType");
            this.userRef = userRef;
            this.documentType = documentType;
        }

        @SerialisationTestConstructor
        private RemoveDocumentUserCreatePermission() {
            this(UserRef.builder().build(), "test");
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public String getDocumentType() {
            return documentType;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class SetDocumentUserCreatePermissions extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;
        @JsonProperty
        private final Set<String> documentTypes;

        @JsonCreator
        public SetDocumentUserCreatePermissions(@JsonProperty("userRef") final UserRef userRef,
                                                @JsonProperty("documentTypes") final Set<String> documentTypes) {
            Objects.requireNonNull(userRef, "Null user ref");
            Objects.requireNonNull(documentTypes, "Null documentTypes");
            this.userRef = userRef;
            this.documentTypes = documentTypes;
        }

        @SerialisationTestConstructor
        private SetDocumentUserCreatePermissions() {
            this(UserRef.builder().build(), Collections.emptySet());
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public Set<String> getDocumentTypes() {
            return documentTypes;
        }
    }


    @JsonInclude(Include.NON_NULL)
    public static final class AddAllDocumentUserCreatePermissions extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;

        @JsonCreator
        public AddAllDocumentUserCreatePermissions(@JsonProperty("userRef") final UserRef userRef) {
            Objects.requireNonNull(userRef, "Null user ref");
            this.userRef = userRef;
        }

        @SerialisationTestConstructor
        private AddAllDocumentUserCreatePermissions() {
            this(UserRef.builder().build());
        }

        public UserRef getUserRef() {
            return userRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class RemoveAllDocumentUserCreatePermissions extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final UserRef userRef;

        @JsonCreator
        public RemoveAllDocumentUserCreatePermissions(@JsonProperty("userRef") final UserRef userRef) {
            Objects.requireNonNull(userRef, "Null user ref");
            this.userRef = userRef;
        }

        @SerialisationTestConstructor
        private RemoveAllDocumentUserCreatePermissions() {
            this(UserRef.builder().build());
        }

        public UserRef getUserRef() {
            return userRef;
        }
    }


    @JsonInclude(Include.NON_NULL)
    public static final class AddAllPermissionsFrom extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final DocRef sourceDocRef;

        @JsonCreator
        public AddAllPermissionsFrom(@JsonProperty("sourceDocRef") final DocRef sourceDocRef) {
            Objects.requireNonNull(sourceDocRef, "Null sourceDocRef");
            this.sourceDocRef = sourceDocRef;
        }

        @SerialisationTestConstructor
        private AddAllPermissionsFrom() {
            this(new DocRef("test", "test"));
        }

        public DocRef getSourceDocRef() {
            return sourceDocRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class SetAllPermissionsFrom extends AbstractDocumentPermissionsChange {

        @JsonProperty
        private final DocRef sourceDocRef;

        @JsonCreator
        public SetAllPermissionsFrom(@JsonProperty("sourceDocRef") final DocRef sourceDocRef) {
            Objects.requireNonNull(sourceDocRef, "Null sourceDocRef");
            this.sourceDocRef = sourceDocRef;
        }

        @SerialisationTestConstructor
        private SetAllPermissionsFrom() {
            this(new DocRef("test", "test"));
        }

        public DocRef getSourceDocRef() {
            return sourceDocRef;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class RemoveAllPermissions extends AbstractDocumentPermissionsChange {

        public RemoveAllPermissions() {
        }
    }
}
