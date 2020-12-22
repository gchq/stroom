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

package stroom.event.logging.impl;

import stroom.docref.DocRef;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.HasId;
import stroom.util.shared.HasUuid;
import stroom.util.shared.PageResponse;

import event.logging.BaseObject;
import event.logging.CopyEventAction;
import event.logging.CopyMoveOutcome;
import event.logging.CreateEventAction;
import event.logging.Criteria;
import event.logging.DeleteEventAction;
import event.logging.Event;
import event.logging.ExportEventAction;
import event.logging.MoveEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.ResultPage;
import event.logging.SearchEventAction;
import event.logging.UpdateEventAction;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.Map;

@Singleton
public class DocumentEventLogImpl implements DocumentEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap;
    private final SecurityContext securityContext;

    @Inject
    public DocumentEventLogImpl(final StroomEventLoggingService eventLoggingService,
                                final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap,
                                final SecurityContext securityContext) {
        this.eventLoggingService = eventLoggingService;
        this.objectInfoProviderMap = objectInfoProviderMap;
        this.securityContext = securityContext;
    }

    private ObjectInfoProvider getInfoAppender(final Class<?> type) {
        ObjectInfoProvider appender = null;

        // Some providers exist for superclasses and not subclass types so keep looking through the class hierarchy to find a provider.
        Class<?> currentType = type;
        Provider<ObjectInfoProvider> provider = null;
        while (currentType != null && provider == null) {
            provider = objectInfoProviderMap.get(new ObjectType(currentType));
            currentType = currentType.getSuperclass();
        }

        if (provider != null) {
            appender = provider.get();
        }

        if (appender == null) {
            LOGGER.error("No appender found for " + type.getName());
        }

        return appender;
    }

    @Override
    public void create(final String objectType, final String objectName, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Create",
                        "Creating " + objectType + " \"" + objectName + "\"",
                        eventDetailBuilder -> eventDetailBuilder
                        .withCreate(CreateEventAction.builder()
                                .addObject(OtherObject.builder()
                                        .withType(objectType)
                                        .withName(objectName)
                                        .build())
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build()));
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to create event!", e);
            }
        });
    }

//    @Override
//    public void create(final Object object) {
//        create(object, null);
//    }

    @Override
    public void create(final Object object, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Create",
                        createEventDescription("Create", object),
                        eventDetailBuilder -> eventDetailBuilder
                                .withCreate(CreateEventAction.builder()
                                        .withObjects(createBaseObject(object))
                                        .withOutcome(EventLoggingUtil.createOutcome(ex))
                                        .build()));
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to create event!", e);
            }
        });
    }

//    @Override
//    public void update(final Object before, final Object after) {
//        update(before, after, null);
//    }

    @Override
    public void update(final Object before, final Object after, final Throwable ex) {
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
                        "Update",
                        createEventDescription("Updating", before),
                        eventDetailBuilder -> eventDetailBuilder
                                .withUpdate(updateBuilder.build()));

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to update event!", e);
            }
        });
    }

//    @Override
//    public void move(final Object before, final Object after) {
//        move(before, after, null);
//    }


    @Override
    public void copy(final Object source, final Object destination, final Throwable ex) {
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
                        "Copy",
                        createEventDescription("Copying", source),
                        eventDetailBuilder -> eventDetailBuilder
                                .withCopy(copyBuilder.build()));

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to copy event!", e);
            }
        });
    }

    @Override
    public void move(final Object source, final Object destination, final Throwable ex) {
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
                        "Move",
                        createEventDescription("Moving", source),
                        eventDetailBuilder -> eventDetailBuilder
                                .withMove(moveBuilder.build()));

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to move event!", e);
            }
        });
    }

    @Override
    public void rename(final Object before, final Object after, final Throwable ex) {
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
                        "Rename",
                        createEventDescription("Renaming", before),
                        eventDetailBuilder -> eventDetailBuilder
                                .withMove(moveBuilder.build()));

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to move event!", e);
            }
        });
    }

    @Override
    public void delete(final Object object, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Delete",
                        createEventDescription("Deleting", object),
                        eventDetailBuilder -> eventDetailBuilder
                                .withDelete(DeleteEventAction.builder()
                                        .withObjects(createBaseObject(object))
                                        .withOutcome(EventLoggingUtil.createOutcome(ex))
                                        .build()));
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to delete event!", e);
            }
        });
    }

    @Override
    public void view(final Object object, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "View",
                        createEventDescription("Viewing", object),
                        eventDetailBuilder -> eventDetailBuilder
                                .withView(ViewEventAction.builder()
                                        .withObjects(createBaseObject(object))
                                        .withOutcome(EventLoggingUtil.createOutcome(ex))
                                        .build()));
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to view event!", e);
            }
        });
    }

    @Override
    public void delete(final BaseCriteria criteria,
                       final Query query,
                       final Long size,
                       final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final Criteria.Builder<Void> criteriaBuilder = Criteria.builder()
                        .withQuery(query);

                if (size != null) {
                    criteriaBuilder.withTotalResults(BigInteger.valueOf(size));
                }

                eventLoggingService.log(
                        criteria.getClass().getSimpleName(),
                        createEventDescription("Finding " + getObjectType(criteria), null),
                        eventDetailBuilder -> eventDetailBuilder
                                .withDelete(DeleteEventAction.builder()
                                        .withObjects(createBaseObject(criteriaBuilder.build()))
                                        .withOutcome(EventLoggingUtil.createOutcome(ex))
                                        .build()));
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to doDelete!", e);
            }
        });
    }

    @Override
    public void download(final Object object, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Download",
                        createEventDescription("Downloading", object),
                        eventDetailBuilder -> eventDetailBuilder
                                .withExport(ExportEventAction.builder()
                                        .withSource(MultiObject.builder()
                                                .withObjects(createBaseObject(object))
                                                .build())
                                        .withOutcome(EventLoggingUtil.createOutcome(ex))
                                        .build()));
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void search(final String typeId,
                       final Query query,
                       final String resultType,
                       final PageResponse pageResponse,
                       final Throwable ex) {

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

                eventLoggingService.log(
                        typeId,
                        createEventDescription("Finding " + resultType, null),
                        eventDetailBuilder -> eventDetailBuilder
                                .withSearch(searchBuilder.build()));

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to doSearch!", e);
            }
        });
    }

//    @Override
//    public void searchSummary(final BaseCriteria criteria, final Query query, final String resultType, final BaseResultList<?> results,
//                              final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction(criteria.getClass().getSimpleName(),
//                        "Finding Summary " + resultType, null);
//                final Search search = new Search();
//                event.getEventDetail().setSearch(search);
//                search.setQuery(query);
//
//                if (results != null && results.getPageResponse() != null) {
//                    final PageResponse pageResponse = results.getPageResponse();
//                    final ResultPage resultPage = getResultPage(pageResponse);
//                    search.setResultPage(resultPage);
//                    if (pageResponse.getTotal() != null) {
//                        search.setTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
//                    }
//                }
//
//                search.setOutcome(EventLoggingUtil.createOutcome(ex));
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to doSearchSummary", e);
//            }
//        });
//    }

    private ResultPage getResultPage(final PageResponse pageResponse) {
        ResultPage resultPage = new ResultPage();
        resultPage.setFrom(BigInteger.valueOf(pageResponse.getOffset()));
        resultPage.setTo(BigInteger.valueOf(pageResponse.getOffset() + pageResponse.getLength()));
        return resultPage;
    }

    private String createEventDescription(final String description,
                                          final Object object) {
        final StringBuilder desc = new StringBuilder(description);
        if (object != null) {
            final String objectType = getObjectType(object);
            if (objectType != null) {
                desc.append(" ");
                desc.append(objectType);
            }

            final String objectName = getObjectName(object);
            if (objectName != null) {
                desc.append(" \"");
                desc.append(objectName);
                desc.append("\"");
            }

            final String objectId = getObjectId(object);
            if (objectId != null) {
                desc.append(" id=");
                desc.append(objectId);
            }
        }
        return desc.toString();
    }

//    private Event createAction(final String typeId, final String description, final Object object) {
//        final StringBuilder desc = new StringBuilder(description);
//        if (object != null) {
//            final String objectType = getObjectType(object);
//            if (objectType != null) {
//                desc.append(" ");
//                desc.append(objectType);
//            }
//
//            final String objectName = getObjectName(object);
//            if (objectName != null) {
//                desc.append(" \"");
//                desc.append(objectName);
//                desc.append("\"");
//            }
//
//            final String objectId = getObjectId(object);
//            if (objectId != null) {
//                desc.append(" id=");
//                desc.append(objectId);
//            }
//        }
//
//        return eventLoggingService.createSkeletonEvent(typeId, desc.toString());
//    }

    private Event createAction(final String typeId,
                               final String description,
                               final String objectType,
                               final String objectName) {
        final String desc = description + " " + objectType + " \"" + objectName;
        return eventLoggingService.createSkeletonEvent(typeId, desc);
    }


    private String getObjectType(final Object object) {
        if (object instanceof DocRef) {
            return String.valueOf(((DocRef) object).getType());
        }

        final ObjectInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
        if (objectInfoAppender == null) {
            return null;
        }
        return objectInfoAppender.getObjectType(object);
    }

    private String getObjectName(final Object object) {
        if (object instanceof DocRef) {
            return ((DocRef) object).getName();
        }
        return null;
    }

    private String getObjectId(final Object object) {
        if (object instanceof HasUuid) {
            return ((HasUuid) object).getUuid();
        }

        if (object instanceof HasId) {
            return String.valueOf(((HasId) object).getId());
        }

        if (object instanceof DocRef) {
            return String.valueOf(((DocRef) object).getUuid());
        }

        return "";
    }

    private BaseObject createBaseObject(final Object object) {
        if (object == null) {
            return null;
        }
        final ObjectInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
        if (objectInfoAppender == null) {
            return null;
        }
        return objectInfoAppender.createBaseObject(object);
    }
}
