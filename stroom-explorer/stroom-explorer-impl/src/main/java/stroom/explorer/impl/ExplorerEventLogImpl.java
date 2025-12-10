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

import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;

import event.logging.CopyEventAction;
import event.logging.CreateEventAction;
import event.logging.DeleteEventAction;
import event.logging.MetaDataTags;
import event.logging.MoveEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.UpdateEventAction;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ExplorerEventLogImpl implements ExplorerEventLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final SecurityContext securityContext;

    @Inject
    ExplorerEventLogImpl(final StroomEventLoggingService eventLoggingService,
                         final SecurityContext securityContext) {
        this.eventLoggingService = eventLoggingService;
        this.securityContext = securityContext;
    }

    @Override
    public void create(final String type,
                       final String uuid,
                       final String name,
                       final DocRef folder,
                       final PermissionInheritance permissionInheritance,
                       final Exception e) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Create",
                        createEventDescription("Creating", type, name, permissionInheritance),
                        CreateEventAction.builder()
                                .addObject(StroomEventLoggingUtil.createOtherObject(type, uuid, name))
                                .withOutcome(EventLoggingUtil.createOutcome(e))
                                .build());
            } catch (final RuntimeException e2) {
                LOGGER.error("Unable to create event!", e2);
            }
        });
    }

    @Override
    public void copy(final DocRef document,
                     final DocRef folder,
                     final PermissionInheritance permissionInheritance,
                     final Exception e) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Copy",
                        createEventDescription("Copying", document, permissionInheritance),
                        CopyEventAction.builder()
                                .withSource(createMultiObject(document))
                                .withDestination(createMultiObject(folder))
                                .withOutcome(StroomEventLoggingUtil.createCopyMoveOutcome(e))
                                .build());
            } catch (final RuntimeException e2) {
                LOGGER.error("Unable to copy event!", e2);
            }
        });
    }

    @Override
    public void move(final DocRef document,
                     final DocRef folder,
                     final PermissionInheritance permissionInheritance,
                     final Exception e) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Move",
                        createEventDescription("Moving", document, permissionInheritance),
                        MoveEventAction.builder()
                                .withSource(createMultiObject(document))
                                .withDestination(createMultiObject(folder))
                                .withOutcome(StroomEventLoggingUtil.createCopyMoveOutcome(e))
                                .build());
            } catch (final RuntimeException e2) {
                LOGGER.error("Unable to move event!", e2);
            }
        });
    }

    @Override
    public void rename(final DocRef document, final String name, final Exception e) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Rename",
                        createEventDescription("Renaming", document, null),
                        MoveEventAction.builder()
                                .withSource(createMultiObject(document))
                                .withDestination(createMultiObject(document, name))
                                .withOutcome(StroomEventLoggingUtil.createCopyMoveOutcome(e))
                                .build());
            } catch (final RuntimeException e2) {
                LOGGER.error("Unable to move event!", e2);
            }
        });
    }

    @Override
    public void update(final ExplorerNode explorerNodeBefore,
                       final ExplorerNode explorerNodeAfter,
                       final Exception ex) {
        securityContext.insecure(() -> {
            try {

                final UpdateEventAction.Builder<Void> actionBuilder = UpdateEventAction.builder();

                if (explorerNodeBefore != null) {
                    actionBuilder.withBefore(MultiObject.builder()
                            .addObject(OtherObject.builder()
                                    .withType(explorerNodeBefore.getType())
                                    .withId(explorerNodeBefore.getUuid())
                                    .withName(explorerNodeBefore.getName())
                                    .withTags(NullSafe.get(explorerNodeBefore.getTags(),
                                            tags -> MetaDataTags.builder()
                                                    .withTags(tags)
                                                    .build()))
                                    .build())
                            .build());
                }

                if (explorerNodeAfter != null) {
                    actionBuilder.withAfter(MultiObject.builder()
                            .addObject(OtherObject.builder()
                                    .withType(explorerNodeAfter.getType())
                                    .withId(explorerNodeAfter.getUuid())
                                    .withName(explorerNodeAfter.getName())
                                    .withTags(NullSafe.get(explorerNodeAfter.getTags(),
                                            tags -> MetaDataTags.builder()
                                                    .withTags(tags)
                                                    .build()))
                                    .build())
                            .build());
                }

                eventLoggingService.log(
                        "Update",
                        createEventDescription(
                                "Updating",
                                NullSafe.firstNonNull(explorerNodeBefore, explorerNodeAfter)
                                        .map(ExplorerNode::getDocRef)
                                        .orElse(null),
                                null),
                        actionBuilder
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());
            } catch (final RuntimeException e2) {
                LOGGER.error("Unable to delete event!", e2);
            }
        });

    }

    @Override
    public void delete(final DocRef document, final Exception e) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Delete",
                        createEventDescription("Deleting", document, null),
                        DeleteEventAction.builder()
                                .addObject(StroomEventLoggingUtil.createOtherObject(document))
                                .withOutcome(EventLoggingUtil.createOutcome(e))
                                .build());
            } catch (final RuntimeException e2) {
                LOGGER.error("Unable to delete event!", e2);
            }
        });
    }

    private String createEventDescription(final String description,
                                          final DocRef docRef,
                                          final PermissionInheritance permissionInheritance) {
        String desc = description;
        if (docRef != null) {
            desc = description + " " + docRef.getType() + " \"" + docRef.getName() + "\" uuid="
                   + docRef.getUuid();
        }
        desc += getPermissionString(permissionInheritance);
        return desc;
    }

    private String createEventDescription(final String description,
                                          final String objectType,
                                          final String objectName,
                                          final PermissionInheritance permissionInheritance) {
        return description
               + " "
               + objectType
               + " \""
               + objectName + "\""
               + getPermissionString(permissionInheritance);
    }


    private String getPermissionString(final PermissionInheritance permissionInheritance) {
        if (permissionInheritance != null) {
            switch (permissionInheritance) {
                case NONE:
                    return " with no permissions";
                case SOURCE:
                    return " with source permissions";
                case DESTINATION:
                    return " with destination permissions";
                case COMBINED:
                    return " with combined permissions";
                default:
                    throw new RuntimeException("Unexpected permissionInheritance " + permissionInheritance);
            }
        }
        return "";
    }

    private MultiObject createMultiObject(final DocRef docRef) {
        if (docRef == null) {
            return null;
        } else {
            return MultiObject.builder()
                    .addObject(StroomEventLoggingUtil.createOtherObject(docRef))
                    .build();
        }
    }

    private MultiObject createMultiObject(final DocRef docRef, final String name) {
        if (docRef == null) {
            return null;
        } else {
            return MultiObject.builder()
                    .addObject(StroomEventLoggingUtil.createOtherObject(docRef.getType(), docRef.getUuid(), name))
                    .build();
        }
    }

}
