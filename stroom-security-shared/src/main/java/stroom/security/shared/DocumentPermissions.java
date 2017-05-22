/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.shared;

import stroom.query.api.v1.DocRef;
import stroom.util.shared.SharedObject;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DocumentPermissions implements SharedObject {
    private static final long serialVersionUID = 5230917643321418827L;

    private DocRef document;
    private String[] allPermissions;
    private Map<UserRef, Set<String>> userPermissions;

    public DocumentPermissions() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocumentPermissions(final DocRef document, final String[] allPermissions,
            final Map<UserRef, Set<String>> userPermissions) {
        this.document = document;
        this.allPermissions = allPermissions;
        this.userPermissions = userPermissions;
    }

    public DocRef getDocument() {
        return document;
    }

    public void setDocument(final DocRef document) {
        this.document = document;
    }

    public String[] getAllPermissions() {
        return allPermissions;
    }

    public void setAllPermissions(final String[] allPermissions) {
        this.allPermissions = allPermissions;
    }

    public Map<UserRef, Set<String>> getUserPermissions() {
        return userPermissions;
    }

    public void setUserPermissions(final Map<UserRef, Set<String>> userPermissions) {
        this.userPermissions = userPermissions;
    }

    public Set<String> getPermissionsForUser(final UserRef userRef) {
        final Set<String> permissions = userPermissions.get(userRef);
        if (permissions != null) {
            return permissions;
        }
        return Collections.emptySet();
    }
}
