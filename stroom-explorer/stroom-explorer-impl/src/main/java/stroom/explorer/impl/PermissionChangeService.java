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

package stroom.explorer.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemovePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.UserRef;

import event.logging.AddGroups;
import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.ComplexLoggedOutcome;
import event.logging.Group;
import event.logging.Permission;
import event.logging.PermissionAttribute;
import event.logging.Permissions;
import event.logging.RemoveGroups;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class PermissionChangeService {
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<DocumentPermissionService> documentPermissionServiceProvider;

    @Inject
    public PermissionChangeService(final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                                   final Provider<DocumentPermissionService> documentPermissionServiceProvider) {
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
    }

    public void changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        final AbstractDocumentPermissionsChange change = request.getChange();

        if (change instanceof final SetPermission setPermission) {
            setPermission(request, setPermission);

        } else if (change instanceof final RemovePermission removePermission) {
            removePermission(request, removePermission);

        } else if (change instanceof final AddDocumentUserCreatePermission addDocumentUserCreatePermission) {
            addDocumentUserCreatePermission(request, addDocumentUserCreatePermission);

        } else if (change instanceof final RemoveDocumentUserCreatePermission removeDocumentUserCreatePermission) {
            removeDocumentUserCreatePermission(request, removeDocumentUserCreatePermission);

        } else if (change instanceof final SetDocumentUserCreatePermissions setDocumentUserCreatePermissions) {
            setDocumentUserCreatePermissions(request, setDocumentUserCreatePermissions);

        } else if (change instanceof final AddAllDocumentUserCreatePermissions addAllDocumentUserCreatePermissions) {
            addAllDocumentUserCreatePermissions(request, addAllDocumentUserCreatePermissions);

        } else if (change instanceof final RemoveAllDocumentUserCreatePermissions
                removeAllDocumentUserCreatePermissions) {
            removeAllDocumentUserCreatePermissions(request, removeAllDocumentUserCreatePermissions);

        } else if (change instanceof final AddAllPermissionsFrom addAllPermissionsFrom) {
            addAllPermissionsFrom(request, addAllPermissionsFrom);

        } else if (change instanceof final SetAllPermissionsFrom setAllPermissionsFrom) {
            setAllPermissionsFrom(request, setAllPermissionsFrom);

        } else if (change instanceof final RemoveAllPermissions removeAllPermissions) {
            removeAllPermissions(request, removeAllPermissions);

        } else {
            throw new RuntimeException("Unexpected change " + change.getClass().getName());
        }
    }


    private PermissionAttribute getPermissionAttribute(final DocumentPermission permission) {
        if (permission == null) {
            return null;
        }
        return switch (permission) {
            case USE -> PermissionAttribute.READ;
            case VIEW -> PermissionAttribute.READ;
            case EDIT -> PermissionAttribute.WRITE;
            case DELETE -> PermissionAttribute.WRITE;
            case OWNER -> PermissionAttribute.OWNER;
        };
    }

    private Group createGroup(final UserRef userRef,
                              final DocumentPermission documentPermission) {
        final PermissionAttribute permissionAttribute = getPermissionAttribute(documentPermission);
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(permissionAttribute)
                .withUser(StroomEventLoggingUtil.createUser(userRef))
                .build();
        return Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
    }

    private void setPermission(final SingleDocumentPermissionChangeRequest request,
                               final SetPermission setPermission) {
        final Group group = createGroup(setPermission.getUserRef(), setPermission.getPermission());
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withAddGroups(AddGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Set document permission")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }


    private void removePermission(final SingleDocumentPermissionChangeRequest request,
                                  final RemovePermission removePermission) {
        final Group group = createGroup(removePermission.getUserRef(), null);
        final AuthoriseEventAction action;
        action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withAddGroups(AddGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Remove document permission")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void addDocumentUserCreatePermission(final SingleDocumentPermissionChangeRequest request,
                                                 final AddDocumentUserCreatePermission
                                                         addDocumentUserCreatePermission) {
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(PermissionAttribute.AUTHOR)
                .withUser(StroomEventLoggingUtil.createUser(addDocumentUserCreatePermission.getUserRef()))
                .withGroup(Group
                        .builder()
                        .withType(addDocumentUserCreatePermission.getDocumentType())
                        .build())
                .build();
        final Group group = Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withAddGroups(AddGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Add document create permission for folder")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void removeDocumentUserCreatePermission(final SingleDocumentPermissionChangeRequest request,
                                                    final RemoveDocumentUserCreatePermission
                                                            removeDocumentUserCreatePermission) {
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(PermissionAttribute.AUTHOR)
                .withUser(StroomEventLoggingUtil.createUser(removeDocumentUserCreatePermission.getUserRef()))
                .withGroup(Group
                        .builder()
                        .withType(removeDocumentUserCreatePermission.getDocumentType())
                        .build())
                .build();
        final Group group = Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this,
                        "changeDocumentPermissions"))
                .withDescription("Remove document create permission for folder")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void setDocumentUserCreatePermissions(final SingleDocumentPermissionChangeRequest request,
                                                  final SetDocumentUserCreatePermissions
                                                          setDocumentUserCreatePermissions) {
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(PermissionAttribute.AUTHOR)
                .withUser(StroomEventLoggingUtil.createUser(setDocumentUserCreatePermissions.getUserRef()))
                .withGroup(Group
                        .builder()
                        .withType(String.join(",", setDocumentUserCreatePermissions.getDocumentTypes()))
                        .build())
                .build();
        final Group group = Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Remove document create permission for folder")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void addAllDocumentUserCreatePermissions(final SingleDocumentPermissionChangeRequest request,
                                                     final AddAllDocumentUserCreatePermissions
                                                             addAllDocumentUserCreatePermissions) {
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(PermissionAttribute.AUTHOR)
                .withUser(StroomEventLoggingUtil.createUser(addAllDocumentUserCreatePermissions.getUserRef()))
                .withGroup(Group.builder().withType(ExplorerConstants.ALL_CREATE_PERMISSIONS).build())
                .build();
        final Group group = Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withAddGroups(AddGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Add all document create permissions for folder")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void removeAllDocumentUserCreatePermissions(final SingleDocumentPermissionChangeRequest request,
                                                        final RemoveAllDocumentUserCreatePermissions
                                                                removeAllDocumentUserCreatePermissions) {
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(PermissionAttribute.AUTHOR)
                .withUser(StroomEventLoggingUtil.createUser(removeAllDocumentUserCreatePermissions.getUserRef()))
                .withGroup(Group.builder().withType(ExplorerConstants.ALL_CREATE_PERMISSIONS).build())
                .build();
        final Group group = Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Remove all document create permissions for folder")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void addAllPermissionsFrom(final SingleDocumentPermissionChangeRequest request,
                                       final AddAllPermissionsFrom
                                               addAllPermissionsFrom) {
        final Group group = Group
                .builder()
                .withType(addAllPermissionsFrom.getSourceDocRef().getType())
                .withId(addAllPermissionsFrom.getSourceDocRef().getUuid())
                .withName(addAllPermissionsFrom.getSourceDocRef().getName())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withAddGroups(AddGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Copy all document permissions from other document")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void setAllPermissionsFrom(final SingleDocumentPermissionChangeRequest request,
                                       final SetAllPermissionsFrom
                                               setAllPermissionsFrom) {
        final Group group = Group
                .builder()
                .withType(setAllPermissionsFrom.getSourceDocRef().getType())
                .withId(setAllPermissionsFrom.getSourceDocRef().getUuid())
                .withName(setAllPermissionsFrom.getSourceDocRef().getName())
                .build();
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .withAddGroups(AddGroups.builder().addGroups(group).build())
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Set all document permissions from other document")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    private void removeAllPermissions(final SingleDocumentPermissionChangeRequest request,
                                      final RemoveAllPermissions
                                              removeAllPermissions) {
        final AuthoriseEventAction action = AuthoriseEventAction
                .builder()
                .withAction(AuthorisationActionType.MODIFY)
                .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                .build();
        stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                .withDescription("Remove all permissions from document")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    final DocumentPermissionService documentPermissionService =
                            documentPermissionServiceProvider.get();
                    final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }
}
