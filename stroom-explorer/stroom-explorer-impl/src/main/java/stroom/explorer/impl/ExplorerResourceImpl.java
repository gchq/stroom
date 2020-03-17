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
 */

package stroom.explorer.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocRefInfo;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceCopyRequest;
import stroom.explorer.shared.ExplorerServiceCreateRequest;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.explorer.shared.ExplorerServiceMoveRequest;
import stroom.explorer.shared.ExplorerServiceRenameRequest;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO : @66 add event logging
class ExplorerResourceImpl implements ExplorerResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerResourceImpl.class);

    private final ExplorerService explorerService;
    private final ExplorerNodeService explorerNodeService;
    private final SecurityContext securityContext;

    @Inject
    ExplorerResourceImpl(final ExplorerService explorerService,
                         final ExplorerNodeService explorerNodeService,
                         final SecurityContext securityContext) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.securityContext = securityContext;
    }

    @Override
    public DocRef create(final ExplorerServiceCreateRequest request) {
        return securityContext.secureResult(() ->
                explorerService.create(request.getDocType(), request.getDocName(), request.getDestinationFolderRef(), request.getPermissionInheritance()));
    }

    @Override
    public BulkActionResult delete(final ExplorerServiceDeleteRequest request) {
        return securityContext.secureResult(() -> explorerService.delete(request.getDocRefs()));
    }

    @Override
    public BulkActionResult copy(final ExplorerServiceCopyRequest request) {
        return securityContext.secureResult(() -> explorerService.copy(request.getDocRefs(), request.getDestinationFolderRef(), request.getPermissionInheritance()));
    }

    @Override
    public BulkActionResult move(final ExplorerServiceMoveRequest request) {
        return securityContext.secureResult(() -> explorerService.move(request.getDocRefs(), request.getDestinationFolderRef(), request.getPermissionInheritance()));
    }

    @Override
    public DocRef rename(final ExplorerServiceRenameRequest request) {
        return securityContext.secureResult(() -> explorerService.rename(request.getDocRef(), request.getDocName()));
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return securityContext.secureResult(() -> {
            final stroom.docref.DocRefInfo docRefInfo = explorerService.info(docRef);

            return new DocRefInfo.Builder()
                    .docRef(docRefInfo.getDocRef())
                    .otherInfo(docRefInfo.getOtherInfo())
                    .createTime(docRefInfo.getCreateTime())
                    .createUser(docRefInfo.getCreateUser())
                    .updateTime(docRefInfo.getUpdateTime())
                    .updateUser(docRefInfo.getUpdateUser())
                    .build();
        });
    }

    @Override
    public Set<DocRef> fetchDocRefs(final Set<DocRef> docRefs) {
        return securityContext.secureResult(() -> {
            final Set<DocRef> result = new HashSet<>();
            if (docRefs != null) {
                for (final DocRef docRef : docRefs) {
                    try {
                        // Only return entries the user has permission to see.
                        if (securityContext.hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.USE)) {
                            explorerNodeService.getNode(docRef)
                                    .map(ExplorerNode::getDocRef)
                                    .ifPresent(result::add);
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                }
            }

            return result;
        });
    }

    @Override
    public DocumentTypes fetchDocumentTypes() {
        return securityContext.secureResult(() -> {
            final List<DocumentType> nonSystemTypes = explorerService.getNonSystemTypes();
            final List<DocumentType> visibleTypes = explorerService.getVisibleTypes();
            return new DocumentTypes(nonSystemTypes, visibleTypes);
        });
    }

    @Override
    public Set<ExplorerNodePermissions> fetchExplorerPermissions(final List<ExplorerNode> explorerNodes) {
        return securityContext.secureResult(() -> {
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
                    docRef = ExplorerConstants.ROOT_DOC_REF;
                }

                // Add special permissions for folders to control creation of sub items.
                if (DocumentTypes.isFolder(docRef.getType())) {
                    for (final DocumentType documentType : explorerService.getNonSystemTypes()) {
                        final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(documentType.getType());
                        if (securityContext.hasDocumentPermission(docRef.getUuid(),
                                permissionName)) {
                            createPermissions.add(documentType.getType());
                        }
                    }
                }

                result.add(new ExplorerNodePermissions(explorerNode, createPermissions, documentPermissions, securityContext.isAdmin()));
            }

            return result;
        });
    }

    @Override
    public FetchExplorerNodeResult fetch(final FindExplorerNodeCriteria request) {
        return securityContext.secureResult(() -> explorerService.getData(request));
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}