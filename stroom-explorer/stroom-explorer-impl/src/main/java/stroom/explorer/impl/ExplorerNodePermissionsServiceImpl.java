/*
 * Copyright 2021 Crown Copyright
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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodePermissionsService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class ExplorerNodePermissionsServiceImpl implements ExplorerNodePermissionsService {

    private final ExplorerService explorerService;
    private final SecurityContext securityContext;

    @Inject
    ExplorerNodePermissionsServiceImpl(final SecurityContext securityContext, final ExplorerService explorerService) {
        this.explorerService = explorerService;
        this.securityContext = securityContext;
    }

    @Override
    public Set<ExplorerNodePermissions> fetchExplorerPermissions(final List<ExplorerNode> explorerNodes) {
        final Set<ExplorerNodePermissions> result = new HashSet<>(explorerNodes.size());

        for (final ExplorerNode explorerNode : explorerNodes) {
            final Set<String> createPermissions = new HashSet<>();
            final Set<String> documentPermissions = new HashSet<>();
            DocRef docRef = explorerNode.getDocRef();

            if (docRef != null) {
                for (final String permissionName : DocumentPermissionNames.DOCUMENT_PERMISSIONS) {
                    if (securityContext.hasDocumentPermission(docRef.getUuid(),
                            permissionName)) {
                        documentPermissions.add(permissionName);
                    }
                }
            }

            // If no entity reference has been passed then assume root folder.
            if (docRef == null) {
                docRef = ExplorerConstants.SYSTEM_DOC_REF;
            }

            // Add special permissions for folders to control creation of sub items.
            if (DocumentTypes.isFolder(docRef.getType())) {
                for (final DocumentType documentType : explorerService.getNonSystemTypes()) {
                    final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(
                            documentType.getType());
                    if (securityContext.hasDocumentPermission(docRef.getUuid(),
                            permissionName)) {
                        createPermissions.add(documentType.getType());
                    }
                }
            }

            result.add(new ExplorerNodePermissions(explorerNode,
                    createPermissions,
                    documentPermissions,
                    securityContext.isAdmin()));
        }

        return result;
    }
}
