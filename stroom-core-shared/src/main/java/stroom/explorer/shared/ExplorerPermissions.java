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

package stroom.explorer.shared;

import stroom.util.shared.SharedObject;

import java.util.Set;

public class ExplorerPermissions implements SharedObject {
    private static final long serialVersionUID = -598649328210958431L;

    private Set<DocumentType> createPermissions;
    private Set<String> documentPermissions;
    private boolean admin;

    public ExplorerPermissions() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerPermissions(final Set<DocumentType> createPermissions, final Set<String> documentPermissions, final boolean admin) {
        this.createPermissions = createPermissions;
        this.documentPermissions = documentPermissions;
        this.admin = admin;
    }

    public Set<DocumentType> getCreatePermissions() {
        return createPermissions;
    }

    public void setCreatePermissions(final Set<DocumentType> createPermissions) {
        this.createPermissions = createPermissions;
    }

    public Set<String> getDocumentPermissions() {
        return documentPermissions;
    }

    public void setDocumentPermissions(final Set<String> documentPermissions) {
        this.documentPermissions = documentPermissions;
    }

    public boolean hasCreatePermission(final DocumentType type) {
        if (admin) {
            return true;
        }
        return createPermissions.contains(type);
    }

    public boolean hasDocumentPermission(final String permission) {
        if (admin) {
            return true;
        }
        return documentPermissions.contains(permission);
    }
}
