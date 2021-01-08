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

import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.meta.shared.FindMetaCriteria;
import stroom.security.api.SecurityContext;

import event.logging.Criteria;
import event.logging.Data;
import event.logging.ExportEventAction;
import event.logging.ImportEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.Query.Builder;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class StreamEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamEventLog.class);

    private final StroomEventLoggingService eventLoggingService;
    private final SecurityContext securityContext;

    @Inject
    StreamEventLog(final StroomEventLoggingService eventLoggingService,
                   final SecurityContext securityContext) {
        this.eventLoggingService = eventLoggingService;
        this.securityContext = securityContext;
    }

    public void importStream(final String feedName, final String path, final Throwable th) {
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Data Upload",
                        "Data uploaded to \"" + feedName + "\"",
                        ImportEventAction.builder()
                                .withSource(MultiObject.builder()
                                        .addObject(OtherObject.builder()
                                                .withType("Stream")
                                                .addData(EventLoggingUtil.createData("Path", path))
                                                .addData(EventLoggingUtil.createData("Feed", feedName))
                                                .build())
                                        .build())
                                .withOutcome(EventLoggingUtil.createOutcome(th))
                                .build());
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to import stream!", e);
            }
        });
    }

    public void exportStream(final FindMetaCriteria findMetaCriteria, final Throwable th) {
        securityContext.insecure(() -> {
            try {
                if (findMetaCriteria != null) {
                    eventLoggingService.log(
                            "ExportData",
                            "Exporting Data",
                            ExportEventAction.builder()
                                    .withSource(MultiObject.builder()
                                            .addCriteria(Criteria.builder()
                                                    .withType("Data")
                                                    .withQuery(createQuery(findMetaCriteria))
                                                    .build())
                                            .build())
                                    .withOutcome(EventLoggingUtil.createOutcome(th))
                                    .build());
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to export stream!", e);
            }
        });
    }

    public void viewStream(final String eventId,
                           final String feedName,
                           final String streamTypeName,
                           final String childStreamType,
                           final DocRef pipelineRef,
                           final Throwable th) {
        securityContext.insecure(() -> {
            try {
                if (eventId != null) {
                    eventLoggingService.log(
                            "ViewStream",
                            "Viewing Stream",
                            ViewEventAction.builder()
                                    .addObject(createStreamObject(
                                            eventId,
                                            feedName,
                                            streamTypeName,
                                            childStreamType,
                                            pipelineRef))
                                    .withOutcome(EventLoggingUtil.createOutcome(th))
                                    .build());
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to view stream!", e);
            }
        });
    }

    private Query createQuery(final FindMetaCriteria findMetaCriteria) {
        if (findMetaCriteria != null) {
            final Builder<Void> queryBuilder = Query.builder();

            StroomEventLoggingUtil.appendExpression(queryBuilder, findMetaCriteria.getExpression());

            return queryBuilder.build();
        }

        return null;
    }

    private OtherObject createStreamObject(final String eventId,
                                           final String feedName,
                                           final String streamTypeName,
                                           final String childStreamType,
                                           final DocRef pipelineRef) {
        final OtherObject object = new OtherObject();
        object.setType("Stream");
        object.setId(eventId);
        if (feedName != null) {
            object.getData().add(EventLoggingUtil.createData("Feed", feedName));
        }
        if (streamTypeName != null) {
            object.getData().add(EventLoggingUtil.createData("StreamType", streamTypeName));
        }
        if (childStreamType != null) {
            object.getData().add(EventLoggingUtil.createData("ChildStreamType", childStreamType));
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

//    private void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindMetaCriteria findMetaCriteria) {
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


//        if (findMetaCriteria.getSelectedIdSet() != null && findMetaCriteria.getSelectedIdSet().size() > 0) {
//            final BaseAdvancedQueryOperator and = new And();
//            items.add(and);
//
//            BaseAdvancedQueryOperator idSetOp = and;
//            if (findMetaCriteria.getSelectedIdSet().size() > 1) {
//                idSetOp = new Or();
//                and.getAdvancedQueryItems().add(idSetOp);
//            }
//
//            for (long id : findMetaCriteria.getSelectedIdSet()) {
//                idSetOp.getAdvancedQueryItems().add(EventLoggingUtil.createTerm(MetaFields.ID.getName(), TermCondition.EQUALS, String.valueOf(id)));
//            }
//
//            appendOperator(and.getAdvancedQueryItems(), findMetaCriteria.getExpression());
//
//        } else {
//            appendOperator(items, findMetaCriteria.getExpression());
//        }
//    }



}
