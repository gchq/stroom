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

package stroom.security.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.entity.shared.EntityServiceException;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.server.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRef;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.shared.ChangeDocumentPermissionsAction;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.UserPermission;
import stroom.security.shared.UserRef;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TaskHandlerBean(task = ChangeDocumentPermissionsAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class ChangeDocumentPermissionsHandler
        extends AbstractTaskHandler<ChangeDocumentPermissionsAction, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeDocumentPermissionsHandler.class);

    private final DocumentPermissionService documentPermissionService;
    private final DocumentPermissionsCache documentPermissionsCache;
    private final SecurityContext securityContext;
    private final ExplorerNodeService explorerNodeService;

    @Inject
    ChangeDocumentPermissionsHandler(final DocumentPermissionService documentPermissionService, final DocumentPermissionsCache documentPermissionsCache, final SecurityContext securityContext, final ExplorerNodeService explorerNodeService) {
        this.documentPermissionService = documentPermissionService;
        this.documentPermissionsCache = documentPermissionsCache;
        this.securityContext = securityContext;
        this.explorerNodeService = explorerNodeService;
    }

    @Override
    public VoidResult exec(final ChangeDocumentPermissionsAction action) {
        final DocRef docRef = action.getDocRef();

        // Check that the current user has permission to change the permissions of the document.
        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.OWNER)) {
            // Record what documents and what users are affected by these changes so we can clear the relevant caches.
            final Set<DocRef> affectedDocRefs = new HashSet<>();
            final Set<UserRef> affectedUserRefs = new HashSet<>();

            // Change the permissions of the document.
            final ChangeSet<UserPermission> changeSet = action.getChangeSet();
            changeDocPermissions(docRef, changeSet, affectedDocRefs, affectedUserRefs, false);

            // Cascade changes if this is a folder and we have been asked to do so.
            if (action.getCascade() != null) {
                cascadeChanges(docRef, changeSet, affectedDocRefs, affectedUserRefs, action.getCascade());
            }

            // Force refresh of cached permissions.
            affectedDocRefs.forEach(documentPermissionsCache::remove);

            return VoidResult.INSTANCE;
        }

        throw new EntityServiceException("You do not have sufficient privileges to change permissions for this document");
    }

    private void changeDocPermissions(final DocRef docRef, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final boolean clear) {
        if (clear) {
            // If we are asked to clear all permissions then get them for this document and then remove them.
            final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(docRef);
            for (final Map.Entry<UserRef, Set<String>> entry : documentPermissions.getUserPermissions().entrySet()) {
                final UserRef userRef = entry.getKey();
                for (final String permission : entry.getValue()) {
                    try {
                        documentPermissionService.removePermission(userRef, docRef, permission);
                        // Remember the affected documents and users so we can clear the relevant caches.
                        affectedDocRefs.add(docRef);
                        affectedUserRefs.add(userRef);
                    } catch (final RuntimeException e) {
                        // Expected.
                        LOGGER.debug(e.getMessage());
                    }
                }
            }

        } else {
            // Otherwise remove permissions specified by the change set.
            for (final UserPermission userPermission : changeSet.getRemoveSet()) {
                final UserRef userRef = userPermission.getUserRef();
                try {
                    documentPermissionService.removePermission(userRef, docRef, userPermission.getPermission());
                    // Remember the affected documents and users so we can clear the relevant caches.
                    affectedDocRefs.add(docRef);
                    affectedUserRefs.add(userRef);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }
            }
        }

        // Add permissions from the change set.
        for (final UserPermission userPermission : changeSet.getAddSet()) {
            // Don't add create permissions to items that aren't folders as it makes no sense.
            if (DocumentTypes.isFolder(docRef.getType()) || !userPermission.getPermission().startsWith(DocumentPermissionNames.CREATE)) {
                final UserRef userRef = userPermission.getUserRef();
                try {
                    documentPermissionService.addPermission(userRef, docRef, userPermission.getPermission());
                    // Remember the affected documents and users so we can clear the relevant caches.
                    affectedDocRefs.add(docRef);
                    affectedUserRefs.add(userRef);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }
            }
        }
    }

//    private void cascadeChanges(final DocRef docRef, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final ChangeDocumentPermissionsAction.Cascade cascade) {
//        final BaseEntity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid());
//        if (entity != null) {
//            if (entity instanceof Folder) {
//                final Folder folder = (Folder) entity;
//
//                switch (cascade) {
//                    case CHANGES_ONLY:
//                        // We are only cascading changes so just pass on the change set.
//                        changeChildPermissions(DocRefUtil.create(folder), changeSet, affectedDocRefs, affectedUserRefs, false);
//                        break;
//
//                    case ALL:
//                        // We are replicating the permissions of the parent folder on all children so create a change set from the parent folder.
//                        final DocumentPermissions parentPermissions = documentPermissionService.getPermissionsForDocument(DocRefUtil.create(folder));
//                        final ChangeSet<UserPermission> fullChangeSet = new ChangeSet<>();
//                        for (final Map.Entry<UserRef, Set<String>> entry : parentPermissions.getUserPermissions().entrySet()) {
//                            final UserRef userRef = entry.getKey();
//                            for (final String permission : entry.getValue()) {
//                                fullChangeSet.add(new UserPermission(userRef, permission));
//                            }
//                        }
//
//                        // Set child permissions to that of the parent folder after clearing all permissions from child documents.
//                        changeChildPermissions(DocRefUtil.create(folder), fullChangeSet, affectedDocRefs, affectedUserRefs, true);
//
//                    break;
//
//                case NO:
//                    // Do nothing.
//                    break;
//            }
//        }
//    }
//
//    private void changeChildPermissions(final DocRef folder, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final boolean clear) {
//        final List<String> types = getTypeList();
//        for (final String type : types) {
//            final List<DocumentEntity> children = genericEntityService.findByFolder(type, folder, null);
//            if (children != null && children.size() > 0) {
//                for (final DocumentEntity child : children) {
//                    final DocRef childDocRef = DocRefUtil.create(child);
//                    changeDocPermissions(childDocRef, changeSet, affectedDocRefs, affectedUserRefs, clear);
//
//                    if (child instanceof Folder) {
//                        changeChildPermissions(childDocRef, changeSet, affectedDocRefs, affectedUserRefs, clear);
//                    }
//                }
//            }
//        }
//    }

    private void cascadeChanges(final DocRef docRef, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final ChangeDocumentPermissionsAction.Cascade cascade) {
        if (DocumentTypes.isFolder(docRef.getType())) {
            switch (cascade) {
                case CHANGES_ONLY:
                    // We are only cascading changes so just pass on the change set.
                    changeDescendantPermissions(docRef, changeSet, affectedDocRefs, affectedUserRefs, false);
                    break;

                case ALL:
                    // We are replicating the permissions of the parent folder on all children so create a change set from the parent folder.
                    final DocumentPermissions parentPermissions = documentPermissionService.getPermissionsForDocument(docRef);
                    final ChangeSet<UserPermission> fullChangeSet = new ChangeSet<>();
                    for (final Map.Entry<UserRef, Set<String>> entry : parentPermissions.getUserPermissions().entrySet()) {
                        final UserRef userRef = entry.getKey();
                        for (final String permission : entry.getValue()) {
                            fullChangeSet.add(new UserPermission(userRef, permission));
                        }
                    }

                    // Set child permissions to that of the parent folder after clearing all permissions from child documents.
                    changeDescendantPermissions(docRef, fullChangeSet, affectedDocRefs, affectedUserRefs, true);

                    break;

                case NO:
                    // Do nothing.
                    break;
            }
        }
    }

    private void changeDescendantPermissions(final DocRef folder, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final boolean clear) {
        final List<ExplorerNode> descendants = explorerNodeService.getDescendants(folder);
        if (descendants != null && descendants.size() > 0) {
            for (final ExplorerNode descendant : descendants) {
                // Ensure that the user has permission to change the permissions of this child.
                if (securityContext.hasDocumentPermission(descendant.getType(), descendant.getUuid(), DocumentPermissionNames.OWNER)) {
                    changeDocPermissions(descendant.getDocRef(), changeSet, affectedDocRefs, affectedUserRefs, clear);
                } else {
                    LOGGER.debug("User does not have permission to change permissions on " + descendant.toString());
                }
            }
        }
    }
}
