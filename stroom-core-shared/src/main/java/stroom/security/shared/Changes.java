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
import stroom.util.shared.UserRef;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class Changes {

    @JsonProperty
    // A map of user/group UUID => Set of permission names to add
    private final Map<String, UserDocPermissionChanges> changes;

    public Changes() {
        this(new HashMap<>());
    }

    @JsonCreator
    public Changes(@JsonProperty("changes") final Map<String, UserDocPermissionChanges> changes) {
        this.changes = changes;
    }

    public void setPermission(final UserRef userRef,
                              final DocumentPermission permission) {
        final UserDocPermissionChanges documentCreateChanges = changes
                .computeIfAbsent(userRef.getUuid(), k -> new UserDocPermissionChanges());
        documentCreateChanges.setPermission(permission);
    }

    public void addDocumentCreatePermission(final UserRef userRef, final String documentType) {
        final UserDocPermissionChanges documentCreateChanges = changes
                .computeIfAbsent(userRef.getUuid(), k -> new UserDocPermissionChanges());
        documentCreateChanges.addDocumentCreatePermission(documentType);
    }

    public void removeDocumentCreatePermission(final UserRef userRef, final String documentType) {
        final UserDocPermissionChanges documentCreateChanges = changes
                .computeIfAbsent(userRef.getUuid(), k -> new UserDocPermissionChanges());
        documentCreateChanges.removeDocumentCreatePermission(documentType);
    }
}
