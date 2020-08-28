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

package stroom.security.identity.account;

import event.logging.BaseAdvancedQueryOperator;
import event.logging.BaseObject;
import event.logging.Event;
import event.logging.Object;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.Search;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class AccountEventLogImpl implements AccountEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap;
    private final SecurityContext securityContext;

    @Inject
    public AccountEventLogImpl(final StroomEventLoggingService eventLoggingService,
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
    public void list(final ResultPage<Account> result, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final BaseAdvancedQueryOperator operator = new BaseAdvancedQueryOperator.And();
//                operator.getAdvancedQueryItems().add( EventLoggingUtil.createTerm("Email", TermCondition.EQUALS, email));

                final Query.Advanced advanced = new Query.Advanced();
                advanced.getAdvancedQueryItems().add(operator);

                final Query query = new Query();
                query.setAdvanced(advanced);

                final Search search = new Search();
                search.setQuery(query);

                final Event event = eventLoggingService.createAction("ListAccounts", "List all accounts");
                event.getEventDetail().setSearch(search);

//                if (pageResponse != null) {
//                    final ResultPage resultPage = getResultPage(pageResponse);
//                    search.setResultPage(resultPage);
//                    if (pageResponse.getTotal() != null) {
//                        search.setTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
//                    }
//                }

                search.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to doSearch!", e);
            }
        });
    }

    @Override
    public void search(final SearchAccountRequest request, final ResultPage<Account> result, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final BaseAdvancedQueryOperator operator = new BaseAdvancedQueryOperator.And();
                operator.getAdvancedQueryItems().add( EventLoggingUtil.createTerm("Email", TermCondition.EQUALS, request.getQuickFilter()));

                final Query.Advanced advanced = new Query.Advanced();
                advanced.getAdvancedQueryItems().add(operator);

                final Query query = new Query();
                query.setAdvanced(advanced);

                final Search search = new Search();
                search.setQuery(query);

                final Event event = eventLoggingService.createAction("SearchAccounts", "Search for accounts by email");
                event.getEventDetail().setSearch(search);

//                if (pageResponse != null) {
//                    final ResultPage resultPage = getResultPage(pageResponse);
//                    search.setResultPage(resultPage);
//                    if (pageResponse.getTotal() != null) {
//                        search.setTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
//                    }
//                }

                search.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to doSearch!", e);
            }
        });
    }



    @Override
    public void create(final CreateAccountRequest request, final Account result, final Throwable ex) {
        securityContext.insecure(() -> {
            try {
                final Event event = eventLoggingService.createAction("CreateAccount", "Create an account");
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setCreate(objectOutcome);
                final BaseObject baseObject = createBaseObject(result);
                objectOutcome.getObjects().add(baseObject);
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to create event!", e);
            }
        });
    }

    @Override
    public void read(final int accountId, final Account result, final Throwable ex) {
//        stroomEventLoggingService.createAction("GetById", "Get a user by ID");
    }

    @Override
    public void read(final String email, final Account result, final Throwable ex) {

    }

    @Override
    public void update(final UpdateAccountRequest request, final int accountId, final Throwable ex) {
//        stroomEventLoggingService.createAction("UpdateUser",
//                "Toggle whether a token is enabled or not.");
    }

    @Override
    public void delete(final int accountId, final Throwable ex) {
//        stroomEventLoggingService.createAction("DeleteUser",
//                "Delete a user by ID");
    }















    private BaseObject createBaseObject(final Account account) {
        String description = null;

//        // Add name.
//        try {
//            description = account.getComments();
//        } catch (final RuntimeException e) {
//            LOGGER.error("Unable to get account description!", e);
//        }

        final Object object = new Object();
        object.setType("Account");
        object.setId(String.valueOf(account.getId()));
        object.setName(account.getUserId());
//        object.setDescription(description);

        try {
            object.getData()
                    .add(EventLoggingUtil.createData("Enabled", String.valueOf(account.isEnabled())));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return object;
    }
}


//
//
//
//
//
//
//
//
//
//
//
////    @Override
////    public void create(final java.lang.Object object) {
////        create(object, null);
////    }
//
//    @Override
//    public void create(final java.lang.Object object, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Create", "Creating", object);
//                final ObjectOutcome objectOutcome = new ObjectOutcome();
//                event.getEventDetail().setCreate(objectOutcome);
//                final BaseObject baseObject = createBaseObject(object);
//                objectOutcome.getObjects().add(baseObject);
//                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to create event!", e);
//            }
//        });
//    }
//
////    @Override
////    public void update(final java.lang.Object before, final java.lang.Object after) {
////        update(before, after, null);
////    }
//
//    @Override
//    public void update(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Update", "Updating", before);
//                final Update update = new Update();
//                event.getEventDetail().setUpdate(update);
//
//                if (before != null) {
//                    final MultiObject bef = new MultiObject();
//                    update.setBefore(bef);
//                    bef.getObjects().add(createBaseObject(before));
//                }
//
//                if (after != null) {
//                    final MultiObject aft = new MultiObject();
//                    update.setAfter(aft);
//                    aft.getObjects().add(createBaseObject(after));
//                }
//
//                update.setOutcome(EventLoggingUtil.createOutcome(ex));
//
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to update event!", e);
//            }
//        });
//    }
//
////    @Override
////    public void move(final java.lang.Object before, final java.lang.Object after) {
////        move(before, after, null);
////    }
//
//
//    @Override
//    public void copy(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Copy", "Copying", before);
//                final CopyMove copy = new CopyMove();
//                event.getEventDetail().setCopy(copy);
//
//                if (before != null) {
//                    final MultiObject source = new MultiObject();
//                    copy.setSource(source);
//                    source.getObjects().add(createBaseObject(before));
//                }
//
//                if (after != null) {
//                    final MultiObject destination = new MultiObject();
//                    copy.setDestination(destination);
//                    destination.getObjects().add(createBaseObject(after));
//                }
//
//                if (ex != null && ex.getMessage() != null) {
//                    final CopyMoveOutcome outcome = new CopyMoveOutcome();
//                    outcome.setSuccess(Boolean.FALSE);
//                    outcome.setDescription(ex.getMessage());
//                    copy.setOutcome(outcome);
//                }
//
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to copy event!", e);
//            }
//        });
//    }
//
//    @Override
//    public void move(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Move", "Moving", before);
//                final CopyMove move = new CopyMove();
//                event.getEventDetail().setMove(move);
//
//                if (before != null) {
//                    final MultiObject source = new MultiObject();
//                    move.setSource(source);
//                    source.getObjects().add(createBaseObject(before));
//                }
//
//                if (after != null) {
//                    final MultiObject destination = new MultiObject();
//                    move.setDestination(destination);
//                    destination.getObjects().add(createBaseObject(after));
//                }
//
//                if (ex != null && ex.getMessage() != null) {
//                    final CopyMoveOutcome outcome = new CopyMoveOutcome();
//                    outcome.setSuccess(Boolean.FALSE);
//                    outcome.setDescription(ex.getMessage());
//                    move.setOutcome(outcome);
//                }
//
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to move event!", e);
//            }
//        });
//    }
//
//    @Override
//    public void rename(final java.lang.Object before, final java.lang.Object after, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Rename", "Renaming", before);
//                final CopyMove move = new CopyMove();
//                event.getEventDetail().setMove(move);
//
//                if (before != null) {
//                    final MultiObject source = new MultiObject();
//                    move.setSource(source);
//                    source.getObjects().add(createBaseObject(before));
//                }
//
//                if (after != null) {
//                    final MultiObject destination = new MultiObject();
//                    move.setDestination(destination);
//                    destination.getObjects().add(createBaseObject(after));
//                }
//
//                if (ex != null && ex.getMessage() != null) {
//                    final CopyMoveOutcome outcome = new CopyMoveOutcome();
//                    outcome.setSuccess(Boolean.FALSE);
//                    outcome.setDescription(ex.getMessage());
//                    move.setOutcome(outcome);
//                }
//
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to rename event!", e);
//            }
//        });
//    }
//
//    @Override
//    public void delete(final java.lang.Object object, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Delete", "Deleting", object);
//                final ObjectOutcome objectOutcome = new ObjectOutcome();
//                event.getEventDetail().setDelete(objectOutcome);
//                final BaseObject baseObject = createBaseObject(object);
//                objectOutcome.getObjects().add(baseObject);
//                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to delete event!", e);
//            }
//        });
//    }
//
//    @Override
//    public void view(final java.lang.Object object, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("View", "Viewing", object);
//                final ObjectOutcome objectOutcome = new ObjectOutcome();
//                event.getEventDetail().setView(objectOutcome);
//                final BaseObject baseObject = createBaseObject(object);
//                objectOutcome.getObjects().add(baseObject);
//                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to view event!", e);
//            }
//        });
//    }
//
//    @Override
//    public void delete(final BaseCriteria criteria, final Query query, final Long size, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction(criteria.getClass().getSimpleName(), "Finding " + getObjectType(criteria),
//                        null);
//
//                final Criteria crit = new Criteria();
//                crit.setQuery(query);
//                if (size != null) {
//                    crit.setTotalResults(BigInteger.valueOf(size));
//                }
//
//                final ObjectOutcome objectOutcome = new ObjectOutcome();
//                objectOutcome.getObjects().add(crit);
//                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(ex));
//
//                event.getEventDetail().setDelete(objectOutcome);
//
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to doDelete!", e);
//            }
//        });
//    }
//
//    @Override
//    public void download(final java.lang.Object object, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction("Download", "Downloading", object);
//
//                final MultiObject multiObject = new MultiObject();
//                multiObject.getObjects().add(createBaseObject(object));
//
//                final Export exp = new Export();
//                exp.setSource(multiObject);
//                exp.setOutcome(EventLoggingUtil.createOutcome(ex));
//
//                event.getEventDetail().setExport(exp);
//
//                eventLoggingService.log(event);
//            } catch (final RuntimeException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//        });
//    }
//
//    @Override
//    public void search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse, final Throwable ex) {
//        securityContext.insecure(() -> {
//            try {
//                final Event event = createAction(typeId, "Finding " + resultType,
//                        null);
//                final Search search = new Search();
//                event.getEventDetail().setSearch(search);
//                search.setQuery(query);
//
//                if (pageResponse != null) {
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
//                LOGGER.error("Unable to doSearch!", e);
//            }
//        });
//    }
//
////    @Override
////    public void searchSummary(final BaseCriteria criteria, final Query query, final String resultType, final BaseResultList<?> results,
////                              final Throwable ex) {
////        securityContext.insecure(() -> {
////            try {
////                final Event event = createAction(criteria.getClass().getSimpleName(),
////                        "Finding Summary " + resultType, null);
////                final Search search = new Search();
////                event.getEventDetail().setSearch(search);
////                search.setQuery(query);
////
////                if (results != null && results.getPageResponse() != null) {
////                    final PageResponse pageResponse = results.getPageResponse();
////                    final ResultPage resultPage = getResultPage(pageResponse);
////                    search.setResultPage(resultPage);
////                    if (pageResponse.getTotal() != null) {
////                        search.setTotalResults(BigInteger.valueOf(pageResponse.getTotal()));
////                    }
////                }
////
////                search.setOutcome(EventLoggingUtil.createOutcome(ex));
////                eventLoggingService.log(event);
////            } catch (final RuntimeException e) {
////                LOGGER.error("Unable to doSearchSummary", e);
////            }
////        });
////    }
//
//    private ResultPage getResultPage(final PageResponse pageResponse) {
//        ResultPage resultPage = new ResultPage();
//        resultPage.setFrom(BigInteger.valueOf(pageResponse.getOffset()));
//        resultPage.setTo(BigInteger.valueOf(pageResponse.getOffset() + pageResponse.getLength()));
//        return resultPage;
//    }
//
//    private Event createAction(final String typeId, final String description, final java.lang.Object object) {
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
//        return eventLoggingService.createAction(typeId, desc.toString());
//    }
//
//    private Event createAction(final String typeId, final String description, final String objectType,
//                               final String objectName) {
//        final String desc = description + " " + objectType + " \"" + objectName;
//        return eventLoggingService.createAction(typeId, desc);
//    }
//
//    private String getObjectType(final java.lang.Object object) {
//        if (object instanceof DocRef) {
//            return String.valueOf(((DocRef) object).getType());
//        }
//
//        final ObjectInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
//        if (objectInfoAppender == null) {
//            return null;
//        }
//        return objectInfoAppender.getObjectType(object);
//    }
//
//    private String getObjectName(final java.lang.Object object) {
//        if (object instanceof DocRef) {
//            return ((DocRef) object).getName();
//        }
//        return null;
//    }
//
//    private String getObjectId(final java.lang.Object object) {
//        if (object instanceof HasUuid) {
//            return ((HasUuid) object).getUuid();
//        }
//
//        if (object instanceof HasId) {
//            return String.valueOf(((HasId) object).getId());
//        }
//
//        if (object instanceof DocRef) {
//            return String.valueOf(((DocRef) object).getUuid());
//        }
//
//        return "";
//    }
//
//    private BaseObject createBaseObject(final java.lang.Object object) {
//        if (object == null) {
//            return null;
//        }
//        final ObjectInfoProvider objectInfoAppender = getInfoAppender(object.getClass());
//        if (objectInfoAppender == null) {
//            return null;
//        }
//        return objectInfoAppender.createBaseObject(object);
//    }
//}
