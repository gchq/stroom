/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UserDocPermissionChanges {

    @JsonProperty
    // A map of user/group UUID => Set of permission names to add
    private DocumentPermission permission;
    @JsonProperty
    private ChangeSet<String> createChanges;

    public UserDocPermissionChanges() {
    }

    @JsonCreator
    public UserDocPermissionChanges(@JsonProperty("permission") final DocumentPermission permission,
                                    @JsonProperty("createChanges") final ChangeSet<String> createChanges) {
        this.permission = permission;
        this.createChanges = createChanges;
    }

    public DocumentPermission getPermission() {
        return permission;
    }

    public void setPermission(final DocumentPermission permission) {
        this.permission = permission;
    }

    public void addDocumentCreatePermission(final String documentType) {
        if (createChanges == null) {
            createChanges = new ChangeSet<>();
        }
        createChanges.add(documentType);
    }

    public void removeDocumentCreatePermission(final String documentType) {
        if (createChanges == null) {
            createChanges = new ChangeSet<>();
        }
        createChanges.remove(documentType);
    }

    @Override
    public String toString() {
        return "UserDocPermissionChanges{" +
                "permission=" + permission +
                ", createChanges=" + createChanges +
                '}';
    }
}
