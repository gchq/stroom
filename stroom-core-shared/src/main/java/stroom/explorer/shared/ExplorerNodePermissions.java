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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

public class ExplorerNodePermissions {
    private ExplorerNode explorerNode;
    private Set<String> createPermissions;
    private Set<String> documentPermissions;
    private boolean admin;

    public ExplorerNodePermissions() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerNodePermissions(final ExplorerNode explorerNode,
                                   final Set<String> createPermissions,
                                   final Set<String> documentPermissions,
                                   final boolean admin) {
        this.explorerNode = explorerNode;
        this.createPermissions = createPermissions;
        this.documentPermissions = documentPermissions;
        this.admin = admin;
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    public void setExplorerNode(final ExplorerNode explorerNode) {
        this.explorerNode = explorerNode;
    }

    public Set<String> getCreatePermissions() {
        return createPermissions;
    }

    public void setCreatePermissions(final Set<String> createPermissions) {
        this.createPermissions = createPermissions;
    }

    public Set<String> getDocumentPermissions() {
        return documentPermissions;
    }

    public void setDocumentPermissions(final Set<String> documentPermissions) {
        this.documentPermissions = documentPermissions;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(final boolean admin) {
        this.admin = admin;
    }

    @JsonIgnore
    public boolean hasCreatePermission(final DocumentType type) {
        if (admin) {
            return true;
        }
        return createPermissions.contains(type.getType());
    }

    @JsonIgnore
    public boolean hasDocumentPermission(final String permission) {
        if (admin) {
            return true;
        }
        return documentPermissions.contains(permission);
    }
}
