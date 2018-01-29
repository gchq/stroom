/*
 * Copyright 2016 Crown Copyright
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
import org.springframework.stereotype.Component;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PageResponse;
import stroom.security.Insecure;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomBeanStore;

import javax.annotation.Resource;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@Insecure
public class EntityEventLogImpl implements EntityEventLog {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(EntityEventLogImpl.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;
    @Resource
    private StroomBeanStore stroomBeanStore;

    private volatile Map<Class<?>, EventInfoProvider> entityInfoAppenders;

    private EventInfoProvider getInfoAppender(final Class<?> type) {
        if (entityInfoAppenders == null) {
            synchronized (this) {
                if (entityInfoAppenders == null) {
                    final Map<Class<?>, EventInfoProvider> appenders = new HashMap<>();
                    final Set<String> beanNames = stroomBeanStore.getStroomBeanByType(EventInfoProvider.class);
                    for (final String beanName : beanNames) {
                        final java.lang.Object bean = stroomBeanStore.getBean(beanName);
                        if (bean instanceof EventInfoProvider) {
                            final EventInfoProvider entityInfoAppender = (EventInfoProvider) bean;
                            appenders.put(entityInfoAppender.getType(), entityInfoAppender);
                        }
                    }
                    entityInfoAppenders = appenders;
                }
            }
        }

        EventInfoProvider appender = entityInfoAppenders.get(type);
        if (appender == null) {
            // Get basic appender.
            appender = entityInfoAppenders.get(null);
        }

        if (appender == null) {
            LOGGER.error("No appender found for " + type.getName());
        }

        return appender;
    }

    @Override
    public void create(final String entityType, final String entityName) {
        create(entityType, entityName, null);
    }

    @Override
    public void create(final String entityType, final String entityName, final Exception ex) {
        try {
            final Event event = createAction("Create", "Creating", entityType, entityName);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setCreate(objectOutcome);

            final Object object = new Object();
            object.setType(entityType);
            object.setName(entityName);

            objectOutcome.getObjects().add(object);
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void create(final BaseEntity entity) {
        create(entity, null);
    }

    @Override
    public void create(final BaseEntity entity, final Exception ex) {
        try {
            final Event event = createAction("Create", "Creating", entity);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setCreate(objectOutcome);
            objectOutcome.getObjects().add(createBaseObject(entity));
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void update(final BaseEntity before, final BaseEntity after) {
        update(before, after, null);
    }

    @Override
    public void update(final BaseEntity before, final BaseEntity after, final Exception ex) {
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
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void move(final BaseEntity before, final BaseEntity after) {
        move(before, after, null);
    }

    @Override
    public void move(final BaseEntity before, final BaseEntity after, final Exception ex) {
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
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void delete(final BaseEntity entity) {
        delete(entity, null);
    }

    @Override
    public void delete(final BaseEntity entity, final Exception ex) {
        try {
            final Event event = createAction("Delete", "Deleting", entity);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setDelete(objectOutcome);
            objectOutcome.getObjects().add(createBaseObject(entity));
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void view(final BaseEntity entity) {
        view(entity, null);
    }

    @Override
    public void view(final BaseEntity entity, final Exception ex) {
        try {
            final Event event = createAction("View", "Viewing", entity);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setView(objectOutcome);
            objectOutcome.getObjects().add(createBaseObject(entity));
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void delete(final BaseCriteria criteria, final Query query, final Long size) {
        doDelete(criteria, query, size, null);
    }

    @Override
    public void delete(final BaseCriteria criteria, final Query query, final Exception ex) {
        doDelete(criteria, query, null, ex);
    }

    private void doDelete(final BaseCriteria criteria, final Query query, final Long size, final Exception ex) {
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
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void download(final BaseEntity entity, final Exception ex) {
        try {
            final Event event = eventLoggingService.createAction("Download", "Downloading " + entity.getType());

            final MultiObject multiObject = new MultiObject();
            multiObject.getObjects().add(createBaseObject(entity));

            final Export exp = new Export();
            exp.setSource(multiObject);
            exp.setOutcome(EventLoggingUtil.createOutcome(ex));

            event.getEventDetail().setExport(exp);

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void search(final BaseCriteria criteria, final Query query, final BaseResultList<?> results) {
        doSearch(criteria, query, results, null);
    }

    @Override
    public void search(final BaseCriteria criteria, final Query query, final Exception ex) {
        doSearch(criteria, query, null, ex);
    }

    private void doSearch(final BaseCriteria criteria, final Query query, final BaseResultList<?> results,
                          final Exception ex) {
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
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    @Override
    public void searchSummary(final BaseCriteria criteria, final Query query, final BaseResultList<?> results) {
        doSearchSummary(criteria, query, results, null);
    }

    @Override
    public void searchSummary(final BaseCriteria criteria, final Query query, final Exception ex) {
        doSearchSummary(criteria, query, null, ex);
    }

    private void doSearchSummary(final BaseCriteria criteria, final Query query, final BaseResultList<?> results,
                                 final Exception ex) {
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
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    private ResultPage getResultPage(final PageResponse pageResponse) {
        ResultPage resultPage = null;
        if (pageResponse.getOffset() != null) {
            if (resultPage == null) {
                resultPage = new ResultPage();
            }
            resultPage.setFrom(BigInteger.valueOf(pageResponse.getOffset()));
        }
        if (pageResponse.getOffset() != null && pageResponse.getOffset() != null && pageResponse.getLength() != null) {
            if (resultPage == null) {
                resultPage = new ResultPage();
            }
            resultPage.setTo(BigInteger.valueOf(pageResponse.getOffset() + pageResponse.getLength()));
        }
        return resultPage;
    }

    private Event createAction(final String typeId, final String description, final BaseEntity entity) {
        String desc = description;
        if (entity != null) {
            if (entity instanceof NamedEntity) {
                final NamedEntity namedEntity = (NamedEntity) entity;
                desc = description + " " + getObjectType(entity) + " \"" + namedEntity.getName() + "\" id="
                        + entity.getId();
            } else {
                desc = description + " " + getObjectType(entity) + " id=" + entity.getId();
            }
        }

        return eventLoggingService.createAction(typeId, desc);
    }

    private Event createAction(final String typeId, final String description, final String entityType,
                               final String entityName) {
        final String desc = description + " " + entityType + " \"" + entityName;
        return eventLoggingService.createAction(typeId, desc);
    }

    private String getObjectType(final java.lang.Object object) {
        final EventInfoProvider entityInfoAppender = getInfoAppender(object.getClass());
        if (entityInfoAppender == null) {
            return null;
        }
        return entityInfoAppender.getObjectType(object);
    }

    private BaseObject createBaseObject(final java.lang.Object object) {
        final EventInfoProvider entityInfoAppender = getInfoAppender(object.getClass());
        if (entityInfoAppender == null) {
            return null;
        }
        return entityInfoAppender.createBaseObject(object);
    }
}
