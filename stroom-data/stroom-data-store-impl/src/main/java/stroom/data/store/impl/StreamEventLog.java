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

package stroom.data.store.impl;

import event.logging.BaseAdvancedQueryItem;
import event.logging.BaseAdvancedQueryOperator;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.BaseAdvancedQueryOperator.Not;
import event.logging.BaseAdvancedQueryOperator.Or;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.Event;
import event.logging.Export;
import event.logging.Import;
import event.logging.MultiObject;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.Query.Advanced;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFieldNames;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.api.Security;

import javax.inject.Inject;
import java.util.List;

public class StreamEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamEventLog.class);

    private final StroomEventLoggingService eventLoggingService;
    private final Security security;

    @Inject
    StreamEventLog(final StroomEventLoggingService eventLoggingService,
                   final Security security) {
        this.eventLoggingService = eventLoggingService;
        this.security = security;
    }

    public void importStream(final String feedName, final String path, final Throwable th) {
        security.insecure(() -> {
            try {
                final Event event = eventLoggingService.createAction("Data Upload", "Data uploaded to \"" + feedName + "\"");

                final event.logging.Object object = new event.logging.Object();
                object.setType("Stream");
                object.getData().add(EventLoggingUtil.createData("Path", path));
                object.getData().add(EventLoggingUtil.createData("Feed", feedName));

                final MultiObject multiObject = new MultiObject();
                multiObject.getObjects().add(object);

                final Import imp = new Import();
                imp.setSource(multiObject);
                imp.setOutcome(EventLoggingUtil.createOutcome(th));

                event.getEventDetail().setImport(imp);

                eventLoggingService.log(event);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to import stream!", e);
            }
        });
    }

    public void exportStream(final FindMetaCriteria findMetaCriteria, final Throwable th) {
        security.insecure(() -> {
            try {
                if (findMetaCriteria != null) {
                    final Event event = eventLoggingService.createAction("ExportData", "Exporting Data");

                    final Criteria criteria = new Criteria();
                    criteria.setType("Data");
                    criteria.setQuery(createQuery(findMetaCriteria));

                    final MultiObject multiObject = new MultiObject();
                    multiObject.getObjects().add(criteria);

                    final Export exp = new Export();
                    exp.setSource(multiObject);
                    exp.setOutcome(EventLoggingUtil.createOutcome(th));

                    event.getEventDetail().setExport(exp);

                    eventLoggingService.log(event);
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to export stream!", e);
            }
        });
    }

    public void viewStream(final String eventId,
                           final String feedName,
                           final String streamTypeName,
                           final DocRef pipelineRef,
                           final Throwable th) {
        security.insecure(() -> {
            try {
                if (eventId != null) {
                    final Event event = eventLoggingService.createAction("View", "Viewing Stream");
                    final ObjectOutcome objectOutcome = new ObjectOutcome();
                    event.getEventDetail().setView(objectOutcome);
                    objectOutcome.getObjects().add(createStreamObject(eventId, feedName, streamTypeName, pipelineRef));
                    objectOutcome.setOutcome(EventLoggingUtil.createOutcome(th));
                    eventLoggingService.log(event);
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to view stream!", e);
            }
        });
    }

    private Query createQuery(final FindMetaCriteria findMetaCriteria) {
        if (findMetaCriteria != null) {
            final Advanced advanced = new Advanced();
            appendCriteria(advanced.getAdvancedQueryItems(), findMetaCriteria);

            final Query query = new Query();
            query.setAdvanced(advanced);

            return query;
        }

        return null;
    }

    private event.logging.Object createStreamObject(final String eventId,
                                                    final String feedName,
                                                    final String streamTypeName,
                                                    final DocRef pipelineRef) {
        final event.logging.Object object = new event.logging.Object();
        object.setType("Stream");
        object.setId(eventId);
        if (feedName != null) {
            object.getData().add(EventLoggingUtil.createData("Feed", feedName));
        }
        if (streamTypeName != null) {
            object.getData().add(EventLoggingUtil.createData("StreamType", streamTypeName));
        }
        if (pipelineRef != null) {
            object.getData().add(convertDocRef("Pipeline", pipelineRef));
        }

        return object;
    }

    private Data convertDocRef(final String name, final DocRef docRef) {
        final Data data = new Data();
        data.setName(name);

        data.getData().add(EventLoggingUtil.createData("type", docRef.getType()));
        data.getData().add(EventLoggingUtil.createData("uuid", docRef.getUuid()));
        data.getData().add(EventLoggingUtil.createData("name", docRef.getName()));

        return data;
    }

    private void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindMetaCriteria findMetaCriteria) {
//        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorIdSet",
//                findMetaCriteria.getStreamProcessorIdSet());
//        CriteriaLoggingUtil.appendIncludeExcludeEntityIdSet(items, "feeds", findMetaCriteria.getFeeds());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "pipelineIdSet", findMetaCriteria.getPipelineUuidCriteria());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "streamTypeIdSet", findMetaCriteria.getStreamTypeIdSet());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "streamIdSet", findMetaCriteria.getMetaIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "statusSet", findMetaCriteria.getStatusSet());
//        CriteriaLoggingUtil.appendRangeTerm(items, "streamIdRange", findMetaCriteria.getStreamIdRange());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "parentStreamIdSet", findMetaCriteria.getParentStreamIdSet());
//        CriteriaLoggingUtil.appendRangeTerm(items, "createPeriod", findMetaCriteria.getCreatePeriod());
//        CriteriaLoggingUtil.appendRangeTerm(items, "effectivePeriod", findMetaCriteria.getEffectivePeriod());
//        CriteriaLoggingUtil.appendRangeTerm(items, "statusPeriod", findMetaCriteria.getStatusPeriod());
//        appendStreamAttributeConditionList(items, "attributeConditionList",
//                findMetaCriteria.getAttributeConditionList());
//
//        CriteriaLoggingUtil.appendPageRequest(items, findMetaCriteria.getPageRequest());
//
//


        if (findMetaCriteria.getSelectedIdSet() != null && findMetaCriteria.getSelectedIdSet().size() > 0) {
            final BaseAdvancedQueryOperator and = new And();
            items.add(and);

            BaseAdvancedQueryOperator idSetOp = and;
            if (findMetaCriteria.getSelectedIdSet().size() > 1) {
                idSetOp = new Or();
                and.getAdvancedQueryItems().add(idSetOp);
            }

            for (long id : findMetaCriteria.getSelectedIdSet()) {
                idSetOp.getAdvancedQueryItems().add(EventLoggingUtil.createTerm(MetaFieldNames.ID, TermCondition.EQUALS, String.valueOf(id)));
            }

            appendOperator(and.getAdvancedQueryItems(), findMetaCriteria.getExpression());

        } else {
            appendOperator(items, findMetaCriteria.getExpression());
        }
    }

    private void appendOperator(final List<BaseAdvancedQueryItem> items, final ExpressionOperator expressionOperator) {
        if (expressionOperator != null && expressionOperator.getEnabled()) {
            BaseAdvancedQueryOperator operator = null;
            switch (expressionOperator.getOp()) {
                case AND:
                    operator = new And();
                    break;
                case OR:
                    operator = new Or();
                    break;
                case NOT:
                    operator = new Not();
                    break;
            }

            items.add(operator);

            if (expressionOperator.getChildren() != null) {
                for (final ExpressionItem item : expressionOperator.getChildren()) {
                    if (item.getEnabled()) {
                        if (item instanceof ExpressionOperator) {
                            appendOperator(operator.getAdvancedQueryItems(), (ExpressionOperator) item);
                        } else if (item instanceof ExpressionTerm) {
                            appendTerm(operator.getAdvancedQueryItems(), (ExpressionTerm) item);
                        }
                    }

                }
            }
        }
    }

    private void appendTerm(final List<BaseAdvancedQueryItem> items, final ExpressionTerm expressionTerm) {
        switch (expressionTerm.getCondition()) {
            case CONTAINS:
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.CONTAINS, expressionTerm.getValue()));
                break;
            case EQUALS:
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.EQUALS, expressionTerm.getValue()));
                break;
            case GREATER_THAN:
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.GREATER_THAN, expressionTerm.getValue()));
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.GREATER_THAN_EQUAL_TO, expressionTerm.getValue()));
                break;
            case LESS_THAN:
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.LESS_THAN, expressionTerm.getValue()));
                break;
            case LESS_THAN_OR_EQUAL_TO:
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.LESS_THAN_EQUAL_TO, expressionTerm.getValue()));
                break;
            case BETWEEN: {
                final String[] parts = expressionTerm.getValue().split(",");
                if (parts.length >= 2) {
                    items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.GREATER_THAN_EQUAL_TO, parts[0]));
                    items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.LESS_THAN, parts[1]));
                } else {
                    items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.EQUALS, expressionTerm.getValue()));
                }
                break;
            }
            case IN: {
                final String[] parts = expressionTerm.getValue().split(",");
                if (parts.length >= 2) {
                    final Or or = new Or();
                    for (final String part : parts) {
                        or.getAdvancedQueryItems().add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.EQUALS, part));
                    }
                    items.add(or);
                } else {
                    items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.EQUALS, expressionTerm.getValue()));
                }
                break;
            }
            case IN_DICTIONARY: {
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.EQUALS, "dictionary: " + expressionTerm.getDictionary()));
            }
            case IS_DOC_REF: {
                items.add(EventLoggingUtil.createTerm(expressionTerm.getField(), TermCondition.EQUALS, "docRef: " + expressionTerm.getDocRef()));
            }
        }
    }
}
