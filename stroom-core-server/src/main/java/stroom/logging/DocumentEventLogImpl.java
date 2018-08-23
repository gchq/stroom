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

package stroom.logging;

import event.logging.BaseObject;
import event.logging.CopyMove;
import event.logging.CopyMoveOutcome;
import event.logging.Criteria;
import event.logging.Criteria.ResultPage;
import event.logging.Event;
import event.logging.Event.EventDetail.Update;
import event.logging.Export;
import event.logging.MultiObject;
import event.logging.Object;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.Search;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasUuid;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PageResponse;
import stroom.lifecycle.StroomBeanStore;
import stroom.security.Security;
import stroom.util.shared.HasId;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class DocumentEventLogImpl implements DocumentEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final Provider<StroomBeanStore> stroomBeanStoreProvider;
    private final Security security;

    private volatile Map<Class<?>, EventInfoProvider> objectInfoAppenders;

    @Inject
    public DocumentEventLogImpl(final StroomEventLoggingService eventLoggingService,
                                final Provider<StroomBeanStore> stroomBeanStoreProvider,
                                final Security security) {
        this.eventLoggingService = eventLoggingService;
        this.stroomBeanStoreProvider = stroomBeanStoreProvider;
        this.security = security;
    }

    private EventInfoProvider getInfoAppender(final Class<?> type) {
        if (objectInfoAppenders == null) {
            synchronized (this) {
                if (objectInfoAppenders == null) {
                    final StroomBeanStore stroomBeanStore = stroomBeanStoreProvider.get();
                    final Map<Class<?>, EventInfoProvider> appenders = new HashMap<>();
                    final Set<EventInfoProvider> eventInfoProviders = stroomBeanStore.getInstancesOfType(EventInfoProvider.class);
                    eventInfoProviders.forEach(eventInfoProvider -> appenders.put(eventInfoProvider.getType(), eventInfoProvider));
                    objectInfoAppenders = appenders;
                }
            }
        }

        EventInfoProvider appender = objectInfoAppenders.get(type);
        if (appender == null) {
            // Get basic appender.
            appender = objectInfoAppenders.get(null);
        }

        if (appender == null) {
            LOGGER.error("No appender found for " + type.getName());
        }

        return appender;
    }

//    @Override
//    public void create(final String objectType, final String objectName) {
//        create(objectType, objectName, null);
//    }

    @Override
    public void create(final String objectType, final String objectName, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Create", "Creating", objectType, objectName);
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setCreate(objectOutcome);

                final Object object = new Object();
                object.setType(objectType);
                object.setName(objectName);

                objectOutcome.getObjects().add(object);
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to create event!", e);
            }
        });
    }

//    @Override
//    public void create(final java.lang.Object object) {
//        create(object, null);
//    }

    @Override
    public void create(final java.lang.Object object, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Create", "Creating", object);
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setCreate(objectOutcome);
                objectOutcome.getObjects().add(createBaseObject(object));
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to create event!", e);
            }
        });
    }

//    @Override
//    public void update(final java.lang.Object before, final java.lang.Object after) {
//        update(before, after, null);
//    }

    @Override
    public void update(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Update", "Updating", before);
                final Update update = new Update();
                event.getEventDetail().setUpdate(update);

                if (before != null) {
                    final MultiObject bef = new MultiObject();
                    update.setBefore(bef);
                    bef.getObjects().add(createBaseObject(before));
                }

                if (after != null) {
                    final MultiObject aft = new MultiObject();
                    update.setAfter(aft);
                    aft.getObjects().add(createBaseObject(after));
                }

                update.setOutcome(EventLoggingUtil.createOutcome(ex));

                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to update event!", e);
            }
        });
    }

//    @Override
//    public void move(final java.lang.Object before, final java.lang.Object after) {
//        move(before, after, null);
//    }


    @Override
    public void copy(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Copy", "Copying", before);
                final CopyMove copy = new CopyMove();
                event.getEventDetail().setCopy(copy);

                if (before != null) {
                    final MultiObject source = new MultiObject();
                    copy.setSource(source);
                    source.getObjects().add(createBaseObject(before));
                }

                if (after != null) {
                    final MultiObject destination = new MultiObject();
                    copy.setDestination(destination);
                    destination.getObjects().add(createBaseObject(after));
                }

                if (ex != null && ex.getMessage() != null) {
                    final CopyMoveOutcome outcome = new CopyMoveOutcome();
                    outcome.setSuccess(Boolean.FALSE);
                    outcome.setDescription(ex.getMessage());
                    copy.setOutcome(outcome);
                }

                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to copy event!", e);
            }
        });
    }

    @Override
    public void move(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Move", "Moving", before);
                final CopyMove move = new CopyMove();
                event.getEventDetail().setMove(move);

                if (before != null) {
                    final MultiObject source = new MultiObject();
                    move.setSource(source);
                    source.getObjects().add(createBaseObject(before));
                }

                if (after != null) {
                    final MultiObject destination = new MultiObject();
                    move.setDestination(destination);
                    destination.getObjects().add(createBaseObject(after));
                }

                if (ex != null && ex.getMessage() != null) {
                    final CopyMoveOutcome outcome = new CopyMoveOutcome();
                    outcome.setSuccess(Boolean.FALSE);
                    outcome.setDescription(ex.getMessage());
                    move.setOutcome(outcome);
                }

                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to move event!", e);
            }
        });
    }

    @Override
    public void rename(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Rename", "Renaming", before);
                final CopyMove move = new CopyMove();
                event.getEventDetail().setMove(move);

                if (before != null) {
                    final MultiObject source = new MultiObject();
                    move.setSource(source);
                    source.getObjects().add(createBaseObject(before));
                }

                if (after != null) {
                    final MultiObject destination = new MultiObject();
                    move.setDestination(destination);
                    destination.getObjects().add(createBaseObject(after));
                }

                if (ex != null && ex.getMessage() != null) {
                    final CopyMoveOutcome outcome = new CopyMoveOutcome();
                    outcome.setSuccess(Boolean.FALSE);
                    outcome.setDescription(ex.getMessage());
                    move.setOutcome(outcome);
                }

                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to rename event!", e);
            }
        });
    }

    //    @Override
//    public void delete(final java.lang.Object object) {
//        delete(object, null);
//    }

    @Override
    public void delete(final java.lang.Object object, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Delete", "Deleting", object);
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setDelete(objectOutcome);
                objectOutcome.getObjects().add(createBaseObject(object));
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to delete event!", e);
            }
        });
    }

    @Override
    public void view(final java.lang.Object object, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("View", "Viewing", object);
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setView(objectOutcome);
                objectOutcome.getObjects().add(createBaseObject(object));
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to view event!", e);
            }
        });
    }

    @Override
    public void delete(final BaseCriteria criteria, final Query query, final Long size) {
        security.insecure(() -> doDelete(criteria, query, size, null));
    }

    @Override
    public void delete(final BaseCriteria criteria, final Query query, final Throwable ex) {
        security.insecure(() -> doDelete(criteria, query, null, ex));
    }

    private void doDelete(final BaseCriteria criteria, final Query query, final Long size, final Throwable ex) {
        try {
            final Event event = createAction(criteria.getClass().getSimpleName(), "Finding " + getObjectType(criteria),
                    null);

            final Criteria crit = new Criteria();
            crit.setQuery(query);
            if (size != null) {
                crit.setTotalResults(BigInteger.valueOf(size));
            }

            final ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(crit);
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));

            event.getEventDetail().setDelete(objectOutcome);

            eventLoggingService.log(event);
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to doDelete!", e);
        }
    }

    @Override
    public void download(final java.lang.Object object, final Throwable ex) {
        security.insecure(() -> {
            try {
                final Event event = createAction("Download", "Downloading", object);

                final MultiObject multiObject = new MultiObject();
                multiObject.getObjects().add(createBaseObject(object));

                final Export exp = new Export();
                exp.setSource(multiObject);
                exp.setOutcome(EventLoggingUtil.createOutcome(ex));

                event.getEventDetail().setExport(exp);

                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void search(final BaseCriteria criteria, final Query query, final BaseResultList<?> results) {
        security.insecure(() -> doSearch(criteria, query, results, null));
    }

    @Override
    public void search(final BaseCriteria criteria, final Query query, final Throwable ex) {
        security.insecure(() -> doSearch(criteria, query, null, ex));
    }

    private void doSearch(final BaseCriteria criteria, final Query query, final BaseResultList<?> results,
                          final Throwable ex) {
        try {
            final Event event = createAction(criteria.getClass().getSimpleName(), "Finding " + getObjectType(criteria),
                    null);
            final Search search = new Search();
            event.getEventDetail().setSearch(search);
            search.setQuery(query);

            if (results != null && results.getPageResponse() != null) {
                final PageResponse pageResponse = results.getPageResponse();
                final ResultPage resultPage = getResultPage(pageResponse);
                search.setResultPage(resultPage);
                if (pageResponse.getTotal() != null) {
                    search.setTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
                }
            }

            search.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to doSearch!", e);
        }
    }

    @Override
    public void searchSummary(final BaseCriteria criteria, final Query query, final BaseResultList<?> results) {
        security.insecure(() -> doSearchSummary(criteria, query, results, null));
    }

    @Override
    public void searchSummary(final BaseCriteria criteria, final Query query, final Throwable ex) {
        security.insecure(() -> doSearchSummary(criteria, query, null, ex));
    }

    private void doSearchSummary(final BaseCriteria criteria, final Query query, final BaseResultList<?> results,
                                 final Throwable ex) {
        try {
            final Event event = createAction(criteria.getClass().getSimpleName(),
                    "Finding Summary " + getObjectType(criteria), null);
            final Search search = new Search();
            event.getEventDetail().setSearch(search);
            search.setQuery(query);

            if (results != null && results.getPageResponse() != null) {
                final PageResponse pageResponse = results.getPageResponse();
                final ResultPage resultPage = getResultPage(pageResponse);
                search.setResultPage(resultPage);
                if (pageResponse.getTotal() != null) {
                    search.setTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
                }
            }

            search.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to doSearchSummary", e);
        }
    }

    private ResultPage getResultPage(final PageResponse pageResponse) {
        ResultPage resultPage = null;
        if (pageResponse.getOffset() != null) {
            resultPage = new ResultPage();
            resultPage.setFrom(BigInteger.valueOf(pageResponse.getOffset()));

            if (pageResponse.getLength() != null) {
                resultPage.setTo(BigInteger.valueOf(pageResponse.getOffset() + pageResponse.getLength()));
            }
        }

        return resultPage;
    }

    private Event createAction(final String typeId, final String description, final java.lang.Object object) {
        String desc = description;
        if (object != null) {
            if (object instanceof NamedEntity) {
                final NamedEntity namedEntity = (NamedEntity) object;
                desc = description + " " + getObjectType(object) + " \"" + namedEntity.getName() + "\" id="
                        + getObjectId(object);
            } else {
                desc = description + " " + getObjectType(object) + " id=" + getObjectId(object);
            }
        }

        return eventLoggingService.createAction(typeId, desc);
    }

    private Event createAction(final String typeId, final String description, final String objectType,
                               final String objectName) {
        final String desc = description + " " + objectType + " \"" + objectName;
        return eventLoggingService.createAction(typeId, desc);
    }

    private String getObjectType(final java.lang.Object object) {
        final EventInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
        if (objectInfoAppender == null) {
            return null;
        }
        return objectInfoAppender.getObjectType(object);
    }

    private String getObjectId(final java.lang.Object object) {
        if (object instanceof HasUuid) {
            return ((HasUuid) object).getUuid();
        }

        if (object instanceof HasId) {
            return String.valueOf(((HasId) object).getId());
        }

        return "";
    }

    private BaseObject createBaseObject(final java.lang.Object object) {
        final EventInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
        if (objectInfoAppender == null) {
            return null;
        }
        return objectInfoAppender.createBaseObject(object);
    }
}
