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

import event.logging.BaseObject;
import event.logging.CopyEventAction;
import event.logging.CopyMoveOutcome;
import event.logging.CreateEventAction;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.DeleteEventAction;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.ExportEventAction;
import event.logging.ImportEventAction;
import event.logging.MoveEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.ProcessEventAction;
import event.logging.Query;
import event.logging.ResultPage;
import event.logging.SearchEventAction;
import event.logging.UnknownEventAction;
import event.logging.UpdateEventAction;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;
import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.HasId;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.HasName;
import stroom.util.shared.HasUuid;
import stroom.util.shared.PageResponse;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        create(objectType, objectName, null, ex);
    }
    @Override
    public void create(final String objectType, final String objectName, final String eventTypeId, final Throwable ex) {
      create(objectType, objectName,  eventTypeId, null, ex);
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
    public void create(final String objectType, final String objectName, final String eventTypeId, final String description, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "Create",
                        description != null ? description : "Creating" + " " + objectType + " \"" + objectName + "\"",
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
                        eventTypeId != null ? eventTypeId : "Create",
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
                                          final Object objectOrObjectType) {
        final String description = Optional.ofNullable(descriptionVerb).orElse(defaultDescription);
        final StringBuilder desc = new StringBuilder(description);
        if (objectOrObjectType != null) {
            if (objectOrObjectType instanceof String){
                //Object type name supplied
                desc.append(" ");
                desc.append(objectOrObjectType);
            } else {
                //Actual object supplied
                final String objectType = getObjectType(objectOrObjectType);
                if (objectType != null) {
                    desc.append(" ");
                    desc.append(objectType);
                }

                final String objectName = getObjectName(objectOrObjectType);
                if (objectName != null) {
                    desc.append(" \"");
                    desc.append(objectName);
                    desc.append("\"");
                }

                final String objectId = getObjectId(objectOrObjectType);
                if (objectId != null) {
                    desc.append(" id=");
                    desc.append(objectId);
                }
            }
        }
        return desc.toString();
    }

//    @Override
//    public void update(final java.lang.Object before, final java.lang.Object after) {
//        update(before, after, null);
//    }

    @Override
    public void update(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        update(before, after, null, ex);
    }

    @Override
    public void upload(final java.lang.Object object, final String eventTypeId, final String descriptionVerb, final Throwable ex) {
        securityContext.insecure(() -> {
            try {

                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "Upload",
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
    public void update(final java.lang.Object before, final java.lang.Object after, final String eventTypeId, final Throwable ex) {
        update(before, after, null, ex);
    }


    @Override
    public void update(final java.lang.Object before, final java.lang.Object after, final String eventTypeId, final String description, final Throwable ex) {
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
                        eventTypeId != null ? eventTypeId : "Update",
                        createEventDescription(description,"Updating", before),
                        updateBuilder.build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log update event!", e);
            }
        });

    }

//    @Override
//    public void move(final java.lang.Object before, final java.lang.Object after) {
//        move(before, after, null);
//    }

    @Override
    public void copy(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        copy(before, after, null, ex);
    }

    @Override
    public void copy(final java.lang.Object source, final java.lang.Object destination, final String eventTypeId, final String description, final Throwable ex) {
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
                        eventTypeId != null ? eventTypeId : "Copy",
                        createEventDescription(description, "Copying", source),
                        copyBuilder.build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log copy event!", e);
            }
        });
    }

    @Override
    public void copy(final java.lang.Object before, final java.lang.Object after, final String eventTypeId, final Throwable ex) {
     copy (before, after, eventTypeId, null, ex);
    }

    @Override
    public void move(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
        move(before, after, null, ex);
    }

    @Override
    public void move(final java.lang.Object before, final java.lang.Object after, final String eventTypeId, final Throwable ex) {
        move(before,after,eventTypeId,null, ex);
    }

    @Override
    public void move(final java.lang.Object source, final java.lang.Object destination, final String eventTypeId, final String verb, final Throwable ex) {
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
                        eventTypeId != null ? eventTypeId : "Move",
                        createEventDescription(verb,"Moving", source),
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
    public void rename(final java.lang.Object before, final java.lang.Object after, final String eventTypeId, final Throwable ex) {
        rename(before, after, eventTypeId, null, ex);
    }

    @Override
    public void rename(final java.lang.Object before, final java.lang.Object after, final String eventTypeId, final String descriptionVerb, final Throwable ex) {
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
                        eventTypeId != null ? eventTypeId : "Rename",
                        createEventDescription(descriptionVerb,"Renaming", before),
                        moveBuilder.build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log rename event!", e);
            }
        });
    }

    @Override
    public void delete(final BaseCriteria criteria, final Query query, final Long size, final String eventTypeId, final String description, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final Criteria.Builder<Void> criteriaBuilder = Criteria.builder()
                        .withQuery(query);

                if (size != null) {
                    criteriaBuilder.withTotalResults(BigInteger.valueOf(size));
                }

                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "Delete by " + criteria.getClass().getSimpleName(),
                        createEventDescription(description, "Delete by criteria", getObjectType(criteria)),
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
    public void delete(final java.lang.Object object, final String eventTypeId, final String descriptionVerb, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "Delete",
                        createEventDescription(descriptionVerb,"Deleting", object),
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
        view (object, eventTypeId, null, ex);
    }


    @Override
    public void view(final java.lang.Object object, final String eventTypeId, final String descriptionVerb, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "View",
                        createEventDescription(descriptionVerb,"Viewing", object),
                        ViewEventAction.builder().withOutcome(EventLoggingUtil.createOutcome(ex)).
                                withObjects(createBaseObject(object)).build());

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
    public void delete(final BaseCriteria criteria, final Query query, final Long size, final String eventTypeId, final Throwable ex) {
      delete(criteria, query, size, eventTypeId, null, ex);
    }

    @Override
    public void download(final java.lang.Object object, final String eventTypeId, final String descriptionVerb, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "Download",
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
    public void process(final java.lang.Object object, final String eventTypeId, final String descriptionVerb, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                ProcessEventAction.Builder<Void> builder = ProcessEventAction.builder().withOutcome(EventLoggingUtil.createOutcome(ex));
                if (object != null){
                    builder = builder.withInput(MultiObject.builder().addObjects(createBaseObject(object)).build());
                }

                eventLoggingService.log(
                        eventTypeId,
                        createEventDescription(descriptionVerb,"Processing", object),
                        builder.build());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void process(final java.lang.Object object, final String eventTypeId, final Throwable ex) {
        process(object, eventTypeId, null, ex);
    }

    @Override
    public void unknownOperation(final java.lang.Object object, final String eventTypeId, final String descriptionVerb,
                                 final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                UnknownEventAction.Builder<Void> builder = UnknownEventAction.builder().withData(getDataItems(object));
                if (ex != null){
                    builder = builder.withData(Data.builder().withName("Error").withValue(ex.getMessage()).build());
                }
                eventLoggingService.log(
                        eventTypeId != null ? eventTypeId : "Unspecified Operation",
                        createEventDescription(descriptionVerb, "No further detail", object),
                        builder.build());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }


    @Override
    public void search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse, final String descriptionVerb, final Throwable ex) {
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
                        typeId != null ? typeId : "Search",
                        createEventDescription(descriptionVerb,"Finding" , resultType),
                        searchBuilder.build());

            } catch (final RuntimeException e) {
                LOGGER.error("Unable to log search event!", e);
            }
        });
    }

    @Override
    public void search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse, final Throwable ex) {
       search(typeId, query, resultType, pageResponse, null, ex);
    }


    private ResultPage getResultPage(final PageResponse pageResponse) {
        ResultPage resultPage = new ResultPage();
        resultPage.setFrom(BigInteger.valueOf(pageResponse.getOffset()));
        resultPage.setTo(BigInteger.valueOf(pageResponse.getOffset() + pageResponse.getLength()));
        return resultPage;
    }


    private String getObjectType(final java.lang.Object object) {
        if (object instanceof DocRef) {
            return String.valueOf(((DocRef) object).getType());
        }

        final ObjectInfoProvider objectInfoProvider = getInfoAppender(object.getClass());
        if (objectInfoProvider == null){
            if (object instanceof Collection){
                Collection collection = (Collection) object;
                if (collection.isEmpty()) {
                    return "Empty collection";
                } else {
                    return "Collection containing " + collection.stream().count()
                            + collection.stream().findFirst().get().getClass().getSimpleName() +
                            " and possibly other objects";
                }
            }
            return object.getClass().getSimpleName();
        }
        return objectInfoProvider.getObjectType(object);
    }

    private String getObjectName(final java.lang.Object object) {
        if (object instanceof DocRef) {
            return ((DocRef) object).getName();
        } else if  (object instanceof HasName){
            return ((HasName) object).getName();
        }

        return null;
    }

    private String getObjectId(final java.lang.Object object) {
        if (object instanceof HasUuid) {
            return ((HasUuid) object).getUuid();
        }

        if (object instanceof HasId) {
            return String.valueOf(((HasId) object).getId());
        }

        if (object instanceof HasIntegerId) {
            return String.valueOf(((HasIntegerId) object).getId());
        }

        if (object instanceof DocRef) {
            return String.valueOf(((DocRef) object).getUuid());
        }

        return null;
    }

    private Iterable<BaseObject> createBaseObject(final java.lang.Object object) {
        if (object == null) {
            return List.of();
        }
        final ObjectInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
        if (objectInfoAppender == null) {
            return List.of(createDefaultBaseObject(object));
        }
        return List.of(objectInfoAppender.createBaseObject(object));
    }

    private BaseObject createDefaultBaseObject(final java.lang.Object object) {
        final OtherObject.Builder<Void> builder = OtherObject.builder()
                .withType(getObjectType(object))
                .withId(getObjectId(object))
                .withName(getObjectName(object));

        builder.addData(getDataItems(object));

        return builder.build();
    }

    static List<Data> getDataItems(java.lang.Object obj){
        if (obj == null){
            return List.of();
        }
        try{
            final Map<String, java.lang.Object> allProps = PropertyUtils.describe(obj);

            return allProps.keySet().stream().map(propName -> {
                java.lang.Object val = allProps.get(propName);

                if (val == null){
                    return null;
                }

                Data d = new Data();
                d.setName(propName);

                if (shouldRedact(propName.toLowerCase())){
                    d.setValue("********");
                } else {
                    d.setValue(val.toString());
                }
                return d;
            }).filter(data -> data != null).collect(Collectors.toList());
        } catch (Exception ex) {
            return List.of();
        }
    }

    //It is possible for a resource to be annotated to prevent it being logged at all, even when the resource
    //itself is logged, e.g. due to configuration settings
    //Assess whether this field should be redacted
    private static boolean shouldRedact (String propNameLowercase){
        //TODO consider replacing or augmenting this hard coding
        // with a mechanism to allow properties to be selected for redaction, e.g. using annotations
        return propNameLowercase.endsWith("password") ||
                propNameLowercase.endsWith("secret") ||
                propNameLowercase.endsWith("token") ||
                propNameLowercase.endsWith("nonce") ||
                propNameLowercase.endsWith("key");
    }
}
