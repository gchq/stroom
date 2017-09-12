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

package stroom.explorer.server;

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
import org.springframework.stereotype.Component;
import stroom.dashboard.server.logging.StroomEventLoggingService;
import stroom.entity.shared.PermissionInheritance;
import stroom.query.api.v1.DocRef;
import stroom.security.Insecure;

import javax.inject.Inject;

@Component
@Insecure
class ExplorerEventLogImpl implements ExplorerEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;

    @Inject
    ExplorerEventLogImpl(final StroomEventLoggingService eventLoggingService) {
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void create(final String type, final String uuid, final String name, final DocRef folder, final PermissionInheritance permissionInheritance, final Exception ex) {
        try {
            final Event event = createAction("Create", "Creating", type, name);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setCreate(objectOutcome);

            final Object object = new Object();
            object.setType(type);
            object.setId(uuid);
            object.setName(name);

            objectOutcome.getObjects().add(object);
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to create event!", e);
        }
    }

    @Override
    public void copy(final DocRef document, final DocRef folder, final PermissionInheritance permissionInheritance, final Exception ex) {
        try {
            final Event event = createAction("Copy", "Copying", document);
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

            if (ex != null && ex.getMessage() != null) {
                final CopyMoveOutcome outcome = new CopyMoveOutcome();
                outcome.setSuccess(Boolean.FALSE);
                outcome.setDescription(ex.getMessage());
                copy.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to copy event!", e);
        }
    }

    @Override
    public void move(final DocRef document, final DocRef folder, final PermissionInheritance permissionInheritance, final Exception ex) {
        try {
            final Event event = createAction("Move", "Moving", document);
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

            if (ex != null && ex.getMessage() != null) {
                final CopyMoveOutcome outcome = new CopyMoveOutcome();
                outcome.setSuccess(Boolean.FALSE);
                outcome.setDescription(ex.getMessage());
                move.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to move event!", e);
        }
    }

    @Override
    public void rename(final DocRef document, final String name, final Exception ex) {
        try {
            final Event event = createAction("Rename", "Renaming", document);
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

            if (ex != null && ex.getMessage() != null) {
                final CopyMoveOutcome outcome = new CopyMoveOutcome();
                outcome.setSuccess(Boolean.FALSE);
                outcome.setDescription(ex.getMessage());
                move.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to move event!", e);
        }
    }

    @Override
    public void delete(final DocRef document, final Exception ex) {
        try {
            final Event event = createAction("Delete", "Deleting", document);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setDelete(objectOutcome);
            objectOutcome.getObjects().add(createBaseObject(document));
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to delete event!", e);
        }
    }

    private Event createAction(final String typeId, final String description, final DocRef entity) {
        String desc = description;
        if (entity != null) {
            desc = description + " " + entity.getType() + " \"" + entity.getName() + "\" id="
                    + entity.getId();
        }

        return eventLoggingService.createAction(typeId, desc);
    }

    private Event createAction(final String typeId, final String description, final String entityType,
                               final String entityName) {
        final String desc = description + " " + entityType + " \"" + entityName;
        return eventLoggingService.createAction(typeId, desc);
    }

    private BaseObject createBaseObject(final DocRef docRef) {
        final Object object = new Object();
        object.setType(docRef.getType());
        object.setId(docRef.getUuid());
        object.setName(docRef.getName());
        return object;
    }
}
