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

import event.logging.BaseObject;
import event.logging.CopyMove;
import event.logging.CopyMoveOutcome;
import event.logging.Event;
import event.logging.MultiObject;
import event.logging.Object;
import event.logging.ObjectOutcome;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.PermissionInheritance;
import stroom.logging.StroomEventLoggingService;
import stroom.query.api.v2.DocRef;
import stroom.security.Insecure;

import javax.inject.Inject;

@Insecure
class ExplorerEventLogImpl implements ExplorerEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;

    @Inject
    ExplorerEventLogImpl(final StroomEventLoggingService eventLoggingService) {
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void create(final String type, final String uuid, final String name, final DocRef folder, final PermissionInheritance permissionInheritance, final Exception e) {
        try {
            final Event event = createAction("Create", "Creating", type, name, permissionInheritance);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setCreate(objectOutcome);

            final Object object = new Object();
            object.setType(type);
            object.setId(uuid);
            object.setName(name);

            objectOutcome.getObjects().add(object);
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(e));
            eventLoggingService.log(event);
        } catch (final RuntimeException e2) {
            LOGGER.error("Unable to create event!", e2);
        }
    }

    @Override
    public void copy(final DocRef document, final DocRef folder, final PermissionInheritance permissionInheritance, final Exception e) {
        try {
            final Event event = createAction("Copy", "Copying", document, permissionInheritance);
            final CopyMove copy = new CopyMove();
            event.getEventDetail().setCopy(copy);

            if (document != null) {
                final MultiObject source = new MultiObject();
                copy.setSource(source);
                source.getObjects().add(createBaseObject(document));
            }

            if (folder != null) {
                final MultiObject destination = new MultiObject();
                copy.setDestination(destination);
                destination.getObjects().add(createBaseObject(folder));
            }

            if (e != null && e.getMessage() != null) {
                final CopyMoveOutcome outcome = new CopyMoveOutcome();
                outcome.setSuccess(Boolean.FALSE);
                outcome.setDescription(e.getMessage());
                copy.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final RuntimeException e2) {
            LOGGER.error("Unable to copy event!", e2);
        }
    }

    @Override
    public void move(final DocRef document, final DocRef folder, final PermissionInheritance permissionInheritance, final Exception e) {
        try {
            final Event event = createAction("Move", "Moving", document, permissionInheritance);
            final CopyMove move = new CopyMove();
            event.getEventDetail().setMove(move);

            if (document != null) {
                final MultiObject source = new MultiObject();
                move.setSource(source);
                source.getObjects().add(createBaseObject(document));
            }

            if (folder != null) {
                final MultiObject destination = new MultiObject();
                move.setDestination(destination);
                destination.getObjects().add(createBaseObject(folder));
            }

            if (e != null && e.getMessage() != null) {
                final CopyMoveOutcome outcome = new CopyMoveOutcome();
                outcome.setSuccess(Boolean.FALSE);
                outcome.setDescription(e.getMessage());
                move.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final RuntimeException e2) {
            LOGGER.error("Unable to move event!", e2);
        }
    }

    @Override
    public void rename(final DocRef document, final String name, final Exception e) {
        try {
            final Event event = createAction("Rename", "Renaming", document, null);
            final CopyMove move = new CopyMove();
            event.getEventDetail().setMove(move);

            if (document != null) {
                final MultiObject source = new MultiObject();
                move.setSource(source);
                source.getObjects().add(createBaseObject(document));
            }

            if (name != null) {
                final DocRef newDoc = new DocRef(document.getType(), document.getUuid(), name);
                final MultiObject destination = new MultiObject();
                move.setDestination(destination);
                destination.getObjects().add(createBaseObject(newDoc));
            }

            if (e != null && e.getMessage() != null) {
                final CopyMoveOutcome outcome = new CopyMoveOutcome();
                outcome.setSuccess(Boolean.FALSE);
                outcome.setDescription(e.getMessage());
                move.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final RuntimeException e2) {
            LOGGER.error("Unable to move event!", e2);
        }
    }

    @Override
    public void delete(final DocRef document, final Exception e) {
        try {
            final Event event = createAction("Delete", "Deleting", document, null);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setDelete(objectOutcome);
            objectOutcome.getObjects().add(createBaseObject(document));
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(e));
            eventLoggingService.log(event);
        } catch (final RuntimeException e2) {
            LOGGER.error("Unable to delete event!", e2);
        }
    }

    private Event createAction(final String typeId,
                               final String description,
                               final DocRef docRef,
                               final PermissionInheritance permissionInheritance) {
        String desc = description;
        if (docRef != null) {
            desc = description + " " + docRef.getType() + " \"" + docRef.getName() + "\" uuid="
                    + docRef.getUuid();
        }
        desc += getPermissionString(permissionInheritance);
        return eventLoggingService.createAction(typeId, desc);
    }

    private Event createAction(final String typeId,
                               final String description,
                               final String objectType,
                               final String objectName,
                               final PermissionInheritance permissionInheritance) {
        String desc = description + " " + objectType + " \"" + objectName + "\"";
        desc += getPermissionString(permissionInheritance);
        return eventLoggingService.createAction(typeId, desc);
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
            }
        }
        return "";
    }

    private BaseObject createBaseObject(final DocRef docRef) {
        final Object object = new Object();
        object.setType(docRef.getType());
        object.setId(docRef.getUuid());
        object.setName(docRef.getName());
        return object;
    }
}
