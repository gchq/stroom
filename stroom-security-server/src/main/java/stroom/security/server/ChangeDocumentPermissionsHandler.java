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

package stroom.security.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.Folder;
import stroom.query.api.DocRef;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.shared.ChangeDocumentPermissionsAction;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.UserPermission;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TaskHandlerBean(task = ChangeDocumentPermissionsAction.class)
@Insecure
public class ChangeDocumentPermissionsHandler
        extends AbstractTaskHandler<ChangeDocumentPermissionsAction, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeDocumentPermissionsHandler.class);

    private final DocumentPermissionService documentPermissionService;
    private final DocumentPermissionsCache documentPermissionsCache;
    private final UserService userService;
    private final UserPermissionsCache userPermissionsCache;
    private final SecurityContext securityContext;
    private final GenericEntityService genericEntityService;
    private volatile List<String> typeList;

    @Inject
    public ChangeDocumentPermissionsHandler(final DocumentPermissionService documentPermissionService, final DocumentPermissionsCache documentPermissionsCache, final UserService userService, final UserPermissionsCache userPermissionsCache, final SecurityContext securityContext, final GenericEntityService genericEntityService) {
        this.documentPermissionService = documentPermissionService;
        this.documentPermissionsCache = documentPermissionsCache;
        this.userService = userService;
        this.userPermissionsCache = userPermissionsCache;
        this.securityContext = securityContext;
        this.genericEntityService = genericEntityService;
    }

    @Override
    public VoidResult exec(final ChangeDocumentPermissionsAction action) {
        final DocRef docRef = action.getDocRef();

        // Check that the current user has permission to change the permissions of the document.
        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.OWNER)) {
            // Record what documents and what users are affected by these changes so we can clear the relevant caches.
            final Set<DocRef> affectedDocRefs = new HashSet<>();
            final Set<UserRef> affectedUserRefs = new HashSet<>();

            // Change the permissions of teh document.
            final ChangeSet<UserPermission> changeSet = action.getChangeSet();
            changeDocPermissions(docRef, changeSet, affectedDocRefs, affectedUserRefs, false);

            // Cascade changes if this is a folder and we have been asked to do so.
            if (action.getCascade() != null) {
                cascadeChanges(docRef, changeSet, affectedDocRefs, affectedUserRefs, action.getCascade());
            }

            // Find out which actual users are affected by changes to user groups.
            final Set<UserRef> affectedUsers = new HashSet<>();
            for (final UserRef affectedUserRef : affectedUserRefs) {
                if (affectedUserRef.isGroup()) {
                    final List<UserRef> users = userService.findUsersInGroup(affectedUserRef);
                    affectedUsers.addAll(users);
                } else {
                    affectedUsers.add(affectedUserRef);
                }
            }

            // Force refresh of cached permissions.
            affectedDocRefs.stream().forEach(documentPermissionsCache::remove);
            affectedUsers.stream().forEach(userPermissionsCache::remove);

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
            if (Folder.ENTITY_TYPE.equals(docRef.getType()) || !userPermission.getPermission().startsWith(DocumentPermissionNames.CREATE)) {
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

    private void cascadeChanges(final DocRef docRef, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final ChangeDocumentPermissionsAction.Cascade cascade) {
        final BaseEntity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid());
        if (entity != null) {
            if (entity instanceof Folder) {
                final Folder folder = (Folder) entity;

                switch (cascade) {
                    case CHANGES_ONLY:
                        // We are only cascading changes so just pass on the change set.
                        changeChildPermissions(DocRefUtil.create(folder), changeSet, affectedDocRefs, affectedUserRefs, false);
                        break;

                    case ALL:
                        // We are replicating the permissions of the parent folder on all children so create a change set from the parent folder.
                        final DocumentPermissions parentPermissions = documentPermissionService.getPermissionsForDocument(DocRefUtil.create(folder));
                        final ChangeSet<UserPermission> fullChangeSet = new ChangeSet<>();
                        for (final Map.Entry<UserRef, Set<String>> entry : parentPermissions.getUserPermissions().entrySet()) {
                            final UserRef userRef = entry.getKey();
                            for (final String permission : entry.getValue()) {
                                fullChangeSet.add(new UserPermission(userRef, permission));
                            }
                        }

                        // Set child permissions to that of the parent folder after clearing all permissions from child documents.
                        changeChildPermissions(DocRefUtil.create(folder), fullChangeSet, affectedDocRefs, affectedUserRefs, true);

                        break;

                    case NO:
                        // Do nothing.
                        break;
                }
            }
        }
    }

    private void changeChildPermissions(final DocRef folder, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<UserRef> affectedUserRefs, final boolean clear) {
        final List<String> types = getTypeList();
        for (final String type : types) {
            final List<DocumentEntity> children = genericEntityService.findByFolder(type, folder, null);
            if (children != null && children.size() > 0) {
                for (final DocumentEntity child : children) {
                    final DocRef childDocRef = DocRefUtil.create(child);
                    changeDocPermissions(childDocRef, changeSet, affectedDocRefs, affectedUserRefs, clear);

                    if (child instanceof Folder) {
                        changeChildPermissions(childDocRef, changeSet, affectedDocRefs, affectedUserRefs, clear);
                    }
                }
            }
        }
    }

    private List<String> getTypeList() {
        if (typeList == null) {
            final List<String> list = new ArrayList<>();
            try {
                final Collection<DocumentEntityService<?>> serviceList = genericEntityService.findAll();
                for (final DocumentEntityService<?> service : serviceList) {
                    final BaseEntity e = service.getEntityClass().newInstance();
                    list.add(e.getType());
                }
            } catch (final IllegalAccessException | InstantiationException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
            typeList = list;
        }
        return typeList;
    }
}
