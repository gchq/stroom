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

package stroom.event.logging.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageResponse;

import event.logging.BaseObject;
import event.logging.CopyEventAction;
import event.logging.CopyMoveOutcome;
import event.logging.CreateEventAction;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.DeleteEventAction;
import event.logging.ExportEventAction;
import event.logging.ImportEventAction;
import event.logging.MoveEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import event.logging.ProcessType;
import event.logging.Query;
import event.logging.ResultPage;
import event.logging.SearchEventAction;
import event.logging.UnknownEventAction;
import event.logging.UpdateEventAction;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Singleton
public class DocumentEventLogImpl implements DocumentEventLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final SecurityContext securityContext;

    @Inject
    public DocumentEventLogImpl(final StroomEventLoggingService eventLoggingService,
                                final SecurityContext securityContext) {
        this.eventLoggingService = eventLoggingService;
        this.securityContext = securityContext;
    }

    @Override
    public void create(final String objectType, final String objectName, final Throwable ex) {
        create(objectType, objectName, null, ex);
    }

    @Override
    public void create(final String objectType, final String objectName, final String eventTypeId, final Throwable ex) {
        create(objectType, objectName, eventTypeId, null, ex);
    }

    @Override
    public void create(final java.lang.Object object, final Throwable ex) {
        create(object, null, ex);
    }

    @Override
    public void create(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        create(object, eventTypeId, null, ex);
    }

    @Override
    public void create(final String objectType,
                       final String objectName,
                       final String eventTypeId,
                       final String description,
                       final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Create",
                        description != null
                                ? description
                                : "Creating" + " " + objectType + " \"" + objectName + "\"",
                        CreateEventAction.builder()
                                .addObject(OtherObject.builder()
                                        .withType(objectType)
                                        .withName(objectName)
                                        .build())
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log create event!", e);
            }
        });
    }

    @Override
    public void create(final java.lang.Object object, final String eventTypeId, final String verb, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Create",
                        createEventDescription(verb, "Creating", object),
                        CreateEventAction.builder()
                                .withObjects(createBaseObject(object))
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log create event!", e);
            }
        });
    }

    private String createEventDescription(final String descriptionVerb,
                                          final String defaultDescription,
                                          final Object object) {
        final String description = Optional.ofNullable(descriptionVerb).orElse(defaultDescription);

        final String objDesc = eventLoggingService.describe(object);

        if (objDesc == null || StroomEventLoggingService.UNKNOWN_OBJECT_DESCRIPTION.equals(objDesc)) {
            return description;
        }
        return description + " " + objDesc;
    }

    @Override
    public void update(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        update(before, after, null, ex);
    }

    @Override
    public void upload(final java.lang.Object object,
                       final String eventTypeId,
                       final String descriptionVerb,
                       final Throwable ex) {
        securityContext.insecure(() -> {
            try {

                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Upload",
                        createEventDescription(descriptionVerb, "Importing", object),
                        ImportEventAction.builder()
                                .withSource(MultiObject.builder()
                                        .withObjects(createBaseObject(object))
                                        .build())
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void update(final java.lang.Object before,
                       final java.lang.Object after,
                       final String eventTypeId,
                       final Throwable ex) {
        update(before, after, eventTypeId, null, ex);
    }

    @Override
    public void update(final java.lang.Object before,
                       final java.lang.Object after,
                       final String eventTypeId,
                       final String description,
                       final Throwable ex) {
        if (before == null && after == null) {
            LOGGER.error("Unable to log update audit event (typeId: '{}', description: '{}'): " +
                            "Either before or after must have a value", eventTypeId, description);
        } else {
            securityContext.insecure(() -> {
                try {
                    final UpdateEventAction.Builder<Void> updateBuilder = UpdateEventAction.builder();

                    if (before != null) {
                        updateBuilder.withBefore(MultiObject.builder()
                                .withObjects(createBaseObject(before))
                                .build());
                    }

                    if (after != null) {
                        updateBuilder.withAfter(MultiObject.builder()
                                .withObjects(createBaseObject(after))
                                .build());
                    }

                    updateBuilder.withOutcome(EventLoggingUtil.createOutcome(ex));

                    eventLoggingService.log(
                            eventTypeId != null
                                    ? eventTypeId
                                    : "Update",
                            createEventDescription(description, "Updating", before),
                            updateBuilder.build());

                } catch (final RuntimeException e) {
                    LOGGER.error("Error logging update audit event (typeId: '{}', description: '{}'): {}",
                            eventTypeId, description, e.getMessage(), e);
                }
            });
        }
    }

    @Override
    public void copy(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        copy(before, after, null, ex);
    }

    @Override
    public void copy(final java.lang.Object source,
                     final java.lang.Object destination,
                     final String eventTypeId,
                     final String description,
                     final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final CopyEventAction.Builder<Void> copyBuilder = CopyEventAction.builder();

                if (source != null) {
                    copyBuilder.withSource(MultiObject.builder()
                            .withObjects(createBaseObject(source))
                            .build());
                }
                if (destination != null) {
                    copyBuilder.withDestination(MultiObject.builder()
                            .withObjects(createBaseObject(destination))
                            .build());
                }
                if (ex != null && ex.getMessage() != null) {
                    copyBuilder.withOutcome(CopyMoveOutcome.builder()
                            .withSuccess(Boolean.FALSE)
                            .withDescription(ex.getMessage())
                            .build());
                }

                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Copy",
                        createEventDescription(description, "Copying", source),
                        copyBuilder.build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log copy event!", e);
            }
        });
    }

    @Override
    public void copy(final java.lang.Object before,
                     final java.lang.Object after,
                     final String eventTypeId,
                     final Throwable ex) {
        copy(before, after, eventTypeId, null, ex);
    }

    @Override
    public void move(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        move(before, after, null, ex);
    }

    @Override
    public void move(final java.lang.Object before,
                     final java.lang.Object after,
                     final String eventTypeId,
                     final Throwable ex) {
        move(before, after, eventTypeId, null, ex);
    }

    @Override
    public void move(final java.lang.Object source,
                     final java.lang.Object destination,
                     final String eventTypeId,
                     final String verb,
                     final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final MoveEventAction.Builder<Void> moveBuilder = MoveEventAction.builder();

                if (source != null) {
                    moveBuilder.withSource(MultiObject.builder()
                            .withObjects(createBaseObject(source))
                            .build());
                }
                if (destination != null) {
                    moveBuilder.withDestination(MultiObject.builder()
                            .withObjects(createBaseObject(destination))
                            .build());
                }
                if (ex != null && ex.getMessage() != null) {
                    moveBuilder.withOutcome(CopyMoveOutcome.builder()
                            .withSuccess(Boolean.FALSE)
                            .withDescription(ex.getMessage())
                            .build());
                }

                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Move",
                        createEventDescription(verb, "Moving", source),
                        moveBuilder.build());
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log move event!", e);
            }
        });
    }

    @Override
    public void rename(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        rename(before, after, null, ex);
    }

    @Override
    public void rename(final java.lang.Object before,
                       final java.lang.Object after,
                       final String eventTypeId,
                       final Throwable ex) {
        rename(before, after, eventTypeId, null, ex);
    }

    @Override
    public void rename(final java.lang.Object before,
                       final java.lang.Object after,
                       final String eventTypeId,
                       final String descriptionVerb,
                       final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final MoveEventAction.Builder<Void> moveBuilder = MoveEventAction.builder();

                if (before != null) {
                    moveBuilder.withSource(MultiObject.builder()
                            .withObjects(createBaseObject(before))
                            .build());
                }
                if (after != null) {
                    moveBuilder.withDestination(MultiObject.builder()
                            .withObjects(createBaseObject(after))
                            .build());
                }
                if (ex != null && ex.getMessage() != null) {
                    moveBuilder.withOutcome(CopyMoveOutcome.builder()
                            .withSuccess(Boolean.FALSE)
                            .withDescription(ex.getMessage())
                            .build());
                }

                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Rename",
                        createEventDescription(descriptionVerb, "Renaming", before),
                        moveBuilder.build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log rename event!", e);
            }
        });
    }

    @Override
    public void delete(final BaseCriteria criteria,
                       final Query query,
                       final Long size,
                       final String eventTypeId,
                       final String description,
                       final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final Criteria.Builder<Void> criteriaBuilder = Criteria.builder()
                        .withQuery(query);

                if (size != null) {
                    criteriaBuilder.withTotalResults(BigInteger.valueOf(size));
                }

                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Delete by " + criteria.getClass().getSimpleName(),
                        createEventDescription(description, "Delete by criteria", criteria),
                        DeleteEventAction.builder()
                                .withObjects(createBaseObject(criteriaBuilder.build()))
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log delete event!", e);
            }
        });
    }

    @Override
    public void delete(final java.lang.Object object,
                       final String eventTypeId,
                       final String descriptionVerb,
                       final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Delete",
                        createEventDescription(descriptionVerb, "Deleting", object),
                        DeleteEventAction.builder()
                                .withObjects(createBaseObject(object))
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log delete event!", e);
            }
        });
    }

    @Override
    public void delete(final java.lang.Object object, final Throwable ex) {
        delete(object, null, ex);
    }

    @Override
    public void delete(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        delete(object, eventTypeId, null, ex);
    }

    @Override
    public void view(final java.lang.Object object, final Throwable ex) {
        view(object, null, ex);
    }

    @Override
    public void view(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        view(object, eventTypeId, null, ex);
    }

    @Override
    public void view(final java.lang.Object object,
                     final String eventTypeId,
                     final String descriptionVerb,
                     final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "View",
                        createEventDescription(descriptionVerb, "Viewing", object),
                        ViewEventAction.builder()
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .withObjects(createBaseObject(object))
                                .build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log view event!", e);
            }
        });
    }

    @Override
    public void delete(final BaseCriteria criteria, final Query query, final Long size, final Throwable ex) {
        delete(criteria, query, size, criteria.getClass().getSimpleName(), ex);
    }

    @Override
    public void delete(final BaseCriteria criteria,
                       final Query query,
                       final Long size,
                       final String eventTypeId,
                       final Throwable ex) {
        delete(criteria, query, size, eventTypeId, null, ex);
    }

    @Override
    public void download(final java.lang.Object object,
                         final String eventTypeId,
                         final String descriptionVerb,
                         final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Download",
                        createEventDescription(descriptionVerb, "Exporting", object),
                        ExportEventAction.builder()
                                .withSource(MultiObject.builder()
                                        .withObjects(createBaseObject(object))
                                        .build())
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void download(final java.lang.Object object, final Throwable ex) {
        download(object, null, ex);
    }

    @Override
    public void download(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        download(object, eventTypeId, null, ex);
    }

    @Override
    public void upload(final java.lang.Object object, final Throwable ex) {
        upload(object, null, ex);
    }

    @Override
    public void upload(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        upload(object, eventTypeId, null, ex);
    }

    @Override
    public void process(final java.lang.Object object,
                        final String eventTypeId,
                        final String descriptionVerb,
                        final Throwable ex,
                        final EventActionDecorator<ProcessEventAction> actionDecorator) {
        securityContext.insecure(() -> {
            try {
                ProcessEventAction.Builder<Void> builder = ProcessEventAction.builder()
                        .withOutcome(EventLoggingUtil.createOutcome(ex));
                if (object != null) {
                    builder = builder.withInput(MultiObject.builder()
                            .addObjects(createBaseObject(object)).build());
                }

                final ProcessEventAction action = builder.withAction(ProcessAction.CALL)
                        .withType(ProcessType.SERVICE)
                        .withCommand("Internal Stroom Processing Invoked")
                        .build();

                eventLoggingService.log(
                        eventTypeId,
                        createEventDescription(descriptionVerb, "Processing", object),
                        actionDecorator != null
                                ? actionDecorator.decorate(action)
                                : action);

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void process(final java.lang.Object object,
                        final String eventTypeId,
                        final String descriptionVerb,
                        final Throwable ex) {
        process(object, eventTypeId, descriptionVerb, ex, null);
    }

    @Override
    public void process(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        process(object, eventTypeId, null, ex);
    }

    @Override
    public void unknownOperation(final java.lang.Object object,
                                 final String eventTypeId,
                                 final String descriptionVerb,
                                 final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                UnknownEventAction.Builder<Void> builder =
                        UnknownEventAction.builder().withData(eventLoggingService.getDataItems(object));
                if (ex != null) {
                    builder = builder.withData(Data.builder().withName("Error").withValue(ex.getMessage()).build());
                }
                eventLoggingService.log(
                        eventTypeId != null
                                ? eventTypeId
                                : "Unspecified Operation",
                        createEventDescription(descriptionVerb, "No further detail", object),
                        builder.build());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse,
                       final String descriptionVerb, final Throwable ex,
                       final EventActionDecorator<SearchEventAction> actionDecorator) {
        securityContext.insecure(() -> {
            try {
                final SearchEventAction.Builder<Void> searchBuilder = SearchEventAction.builder()
                        .withQuery(query)
                        .withOutcome(EventLoggingUtil.createOutcome(ex));

                if (pageResponse != null) {
                    searchBuilder.withResultPage(getResultPage(pageResponse));
                    if (pageResponse.getTotal() != null) {
                        searchBuilder.withTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
                    }
                }

                final SearchEventAction action = searchBuilder.build();

                eventLoggingService.log(
                        typeId != null
                                ? typeId
                                : "Search",
                        createEventDescription(descriptionVerb, "Finding", resultType),
                        actionDecorator != null
                                ? actionDecorator.decorate(action)
                                : action);

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log search event!", e);
            }
        });

    }

    @Override
    public void search(final String typeId, final Query query, final String resultType,
                       final PageResponse pageResponse, final String descriptionVerb, final Throwable ex) {
        search(typeId, query, resultType, pageResponse, descriptionVerb, ex, null);
    }

    @Override
    public void search(final String typeId,
                       final Query query,
                       final String resultType,
                       final PageResponse pageResponse,
                       final Throwable ex) {
        search(typeId, query, resultType, pageResponse, null, ex);
    }

    private ResultPage getResultPage(final PageResponse pageResponse) {
        final ResultPage resultPage = new ResultPage();
        resultPage.setFrom(BigInteger.valueOf(pageResponse.getOffset()));
        resultPage.setTo(BigInteger.valueOf(pageResponse.getOffset() + pageResponse.getLength()));
        return resultPage;
    }

    private Iterable<BaseObject> createBaseObject(final java.lang.Object object) {
        return List.of(eventLoggingService.convert(object));
    }

}
