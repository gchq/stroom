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

package stroom.process.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.explorer.server.ExplorerService;
import stroom.process.shared.FetchProcessorAction;
import stroom.process.shared.StreamProcessorFilterRow;
import stroom.process.shared.StreamProcessorRow;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.server.StreamProcessorFilterService;
import stroom.streamtask.server.StreamProcessorService;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.Expander;
import stroom.util.shared.SharedObject;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = FetchProcessorAction.class)
@Scope(StroomScope.TASK)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class FetchProcessorHandler extends AbstractTaskHandler<FetchProcessorAction, ResultList<SharedObject>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchProcessorHandler.class);

    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private StreamProcessorService streamProcessorService;
    @Resource
    private SecurityContext securityContext;
    @Resource
    private ExplorerService explorerService;
    @Resource
    private ExpressionToFindStreamProcessorFilterCriteria expressionToFindStreamProcessorFilterCriteria;
    @Resource
    private ExpressionToFindStreamProcessorCriteria expressionToFindStreamProcessorCriteria;

    @Override
    public ResultList<SharedObject> exec(final FetchProcessorAction action) {
        final List<SharedObject> values = new ArrayList<>();

        final FindStreamProcessorFilterCriteria criteria = expressionToFindStreamProcessorFilterCriteria.convert(action.getExpression());
        final FindStreamProcessorCriteria criteriaRoot = expressionToFindStreamProcessorCriteria.convert(action.getExpression());
//        if (action.getExpression() != null) {
//            criteria.obtainPipelineIdSet().add(action.getPipelineId());
//            criteriaRoot.obtainPipelineIdSet().add(action.getPipelineId());
//
//        } else if (action.getFolder() != null) {
//            final Set<DocRef> pipelines = explorerService.getDescendants(action.getFolder(), PipelineEntity.ENTITY_TYPE);
//            pipelines.forEach(pipeline -> {
//                try {
//                    final PipelineEntity pipelineEntity = pipelineService.loadByUuidWithoutUnmarshal(pipeline.getUuid());
//                    if (pipelineEntity != null) {
//                        criteria.obtainPipelineIdSet().add(pipelineEntity.getId());
//                        criteriaRoot.obtainPipelineIdSet().add(pipelineEntity.getId());
//                    }
//                } catch (final RuntimeException e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//            });
//        }

        // If the user is not an admin then only show them filters that were created by them.
        if (!securityContext.isAdmin()) {
            criteria.setCreateUser(securityContext.getUserId());
        }

//        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
//        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
//        criteriaRoot.getFetchSet().add(PipelineEntity.ENTITY_TYPE);

        final BaseResultList<StreamProcessor> streamProcessors = streamProcessorService.find(criteriaRoot);

        final BaseResultList<StreamProcessorFilter> streamProcessorFilters = streamProcessorFilterService
                .find(criteria);

        // Get unique processors.
        final Set<StreamProcessor> processors = new HashSet<>();
        processors.addAll(streamProcessors);

        final List<StreamProcessor> sorted = new ArrayList<>(processors);
        Collections.sort(sorted, (o1, o2) -> {
            if (o1.getPipeline() != null && o2.getPipeline() != null) {
                return o1.getPipeline().getName().compareTo(o2.getPipeline().getName());
            }
            if (o1.getPipeline() != null) {
                return -1;
            }
            if (o2.getPipeline() != null) {
                return 1;
            }
            return o1.compareTo(o2);
        });

        for (final StreamProcessor streamProcessor : sorted) {
            final Expander processorExpander = new Expander(0, false, false);
            final StreamProcessorRow streamProcessorRow = new StreamProcessorRow(processorExpander,
                    streamProcessor);
            values.add(streamProcessorRow);

            // If the job row is open then add child rows.
            if (action.getExpandedRows() == null || action.isRowExpanded(streamProcessorRow)) {
                processorExpander.setExpanded(true);

                // Add filters.
                for (final StreamProcessorFilter streamProcessorFilter : streamProcessorFilters) {
                    if (streamProcessor.equals(streamProcessorFilter.getStreamProcessor())) {

                        // Decorate the expression with resolved dictionaries etc.
                        final QueryData queryData = streamProcessorFilter.getQueryData();
                        if (queryData != null && queryData.getExpression() != null) {
                            queryData.setExpression(decorate(queryData.getExpression()));
                        }

                        final StreamProcessorFilterRow streamProcessorFilterRow = new StreamProcessorFilterRow(
                                streamProcessorFilter);
                        values.add(streamProcessorFilterRow);
                    }
                }
            }
        }

        return BaseResultList.createUnboundedList(values);
    }

    private ExpressionOperator decorate(final ExpressionOperator operator) {
        final ExpressionOperator.Builder builder = new Builder()
                .op(operator.getOp())
                .enabled(operator.getEnabled());

        if (operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child instanceof ExpressionOperator) {
                    builder.addOperator(decorate((ExpressionOperator) child));

                } else if (child instanceof ExpressionTerm) {
                    ExpressionTerm term = (ExpressionTerm) child;
                    DocRef docRef = term.getDocRef();

                    try {
                        if (docRef != null) {
                            final DocRefInfo docRefInfo = explorerService.info(docRef);
                            if (docRefInfo != null) {
                                term = new ExpressionTerm.Builder()
                                        .enabled(term.getEnabled())
                                        .field(term.getField())
                                        .condition(term.getCondition())
                                        .value(term.getValue())
                                        .docRef(docRefInfo.getDocRef())
                                        .build();
                            }
//
//                        if (DictionaryDoc.ENTITY_TYPE.equals(docRef.getType())) {
//                            try {
//                                final DictionaryDoc dictionaryDoc = dictionaryStore.read(docRef.getUuid());
//                                docRef = stroom.entity.shared.DocRefUtil.create(dictionaryDoc);
//                            } catch (final RuntimeException e) {
//                                LOGGER.debug(e.getMessage(), e);
//                            }
//
//                            term = new ExpressionTerm.Builder()
//                                    .enabled(term.getEnabled())
//                                    .field(term.getField())
//                                    .condition(term.getCondition())
//                                    .value(term.getValue())
//                                    .docRef(docRef)
//                                    .build();
//                        }
//
//                        if (PipelineEntity.ENTITY_TYPE.equals(docRef.getType())) {
//                            try {
//                                final PipelineEntity pipelineEntity = pipelineService.loadByUuid(docRef.getUuid());
//                                docRef = stroom.entity.shared.DocRefUtil.create(pipelineEntity);
//                            } catch (final RuntimeException e) {
//                                LOGGER.debug(e.getMessage(), e);
//                            }
//
//                            term = new ExpressionTerm.Builder()
//                                    .enabled(term.getEnabled())
//                                    .field(term.getField())
//                                    .condition(term.getCondition())
//                                    .value(term.getValue())
//                                    .docRef(docRef)
//                                    .build();
//                        }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }

                    builder.addTerm(term);
                }
            }
        }

        return builder.build();
    }
}
