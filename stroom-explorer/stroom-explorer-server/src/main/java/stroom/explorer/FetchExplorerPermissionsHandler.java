/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.explorer;

import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerPermissions;
import stroom.explorer.shared.FetchExplorerPermissionsAction;
import stroom.docref.DocRef;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.util.shared.SharedMap;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TaskHandlerBean(task = FetchExplorerPermissionsAction.class)
class FetchExplorerPermissionsHandler
        extends AbstractTaskHandler<FetchExplorerPermissionsAction, SharedMap<ExplorerNode, ExplorerPermissions>> {
    private final ExplorerService explorerService;
    private final SecurityContext securityContext;
    private final Security security;

    @Inject
    FetchExplorerPermissionsHandler(final ExplorerService explorerService,
                                    final SecurityContext securityContext,
                                    final Security security) {
        this.explorerService = explorerService;
        this.securityContext = securityContext;
        this.security = security;
    }

    @Override
    public SharedMap<ExplorerNode, ExplorerPermissions> exec(final FetchExplorerPermissionsAction action) {
        return security.secureResult(() -> {
            final List<ExplorerNode> explorerNodeList = action.getExplorerNodeList();
            final Map<ExplorerNode, ExplorerPermissions> resultMap = new HashMap<>();

            for (final ExplorerNode explorerNode : explorerNodeList) {
                final Set<DocumentType> createPermissions = new HashSet<>();
                final Set<String> documentPermissions = new HashSet<>();
                DocRef docRef = explorerNode.getDocRef();

                if (docRef != null) {
                    for (final String permissionName : DocumentPermissionNames.DOCUMENT_PERMISSIONS) {
                        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(),
                                permissionName)) {
                            documentPermissions.add(permissionName);
                        }
                    }
                }

                // If no entity reference has been passed then assume root folder.
                if (docRef == null) {
                    docRef = ExplorerConstants.ROOT_DOC_REF;
                }

                // Add special permissions for folders to control creation of sub items.
                if (DocumentTypes.isFolder(docRef.getType())) {
                    final DocumentTypes documentTypes = explorerService.getDocumentTypes();
                    for (final DocumentType documentType : documentTypes.getNonSystemTypes()) {
                        final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(documentType.getType());
                        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(),
                                permissionName)) {
                            createPermissions.add(documentType);
                        }
                    }
                }

                resultMap.put(explorerNode, new ExplorerPermissions(createPermissions, documentPermissions, securityContext.isAdmin()));
            }

            return new SharedMap<>(resultMap);
        });
    }
}
