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

package stroom.explorer.shared;

import stroom.docstore.shared.DocumentType;
import stroom.security.shared.DocumentPermission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class ExplorerNodePermissions {

    @JsonProperty
    private final ExplorerNode explorerNode;
    @JsonProperty
    private final Set<String> createPermissions;
    @JsonProperty
    private final Set<DocumentPermission> documentPermissions;
    @JsonProperty
    private final boolean admin;

    @JsonCreator
    public ExplorerNodePermissions(@JsonProperty("explorerNode") final ExplorerNode explorerNode,
                                   @JsonProperty("createPermissions") final Set<String> createPermissions,
                                   @JsonProperty("documentPermissions") final Set<DocumentPermission>
                                           documentPermissions,
                                   @JsonProperty("admin") final boolean admin) {
        this.explorerNode = explorerNode;
        this.createPermissions = createPermissions;
        this.documentPermissions = documentPermissions;
        this.admin = admin;
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    public boolean hasCreatePermission(final DocumentType type) {
        if (admin) {
            return true;
        }
        return createPermissions.contains(type.getType());
    }

    public boolean hasCreatePermission(final String type) {
        if (admin) {
            return true;
        }
        return createPermissions.contains(type);
    }

    public boolean hasDocumentPermission(final DocumentPermission permission) {
        if (admin) {
            return true;
        }
        return documentPermissions.contains(permission);
    }
}
