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
 */

package stroom.streamtask;


import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.StringCriteria;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.persist.EntityManagerSupport;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterTracker;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
class StreamProcessorFilterServiceImpl
        extends SystemEntityServiceImpl<StreamProcessorFilter, FindStreamProcessorFilterCriteria>
        implements StreamProcessorFilterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamProcessorFilterServiceImpl.class);

    private final Security security;
    private final EntityManagerSupport entityManagerSupport;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterMarshaller marshaller;
    private final ExpressionToFindCriteria expressionToFindCriteria;
    private final PipelineStore pipelineStore;

    @Inject
    StreamProcessorFilterServiceImpl(
            final StroomEntityManager entityManager,
            final Security security,
            final EntityManagerSupport entityManagerSupport,
            final StreamProcessorService streamProcessorService,
            final ExpressionToFindCriteria expressionToFindCriteria,
            @Named("cachedPipelineStore") final PipelineStore pipelineStore) {
        super(entityManager, security);
        this.pipelineStore = pipelineStore;
        this.security = security;
        this.entityManagerSupport = entityManagerSupport;
        this.streamProcessorService = streamProcessorService;
        this.marshaller = new StreamProcessorFilterMarshaller();
        this.expressionToFindCriteria = expressionToFindCriteria;
    }

    @Override
    public StreamProcessorFilter save(final StreamProcessorFilter entity) {
        expressionToFindCriteria.convert(entity.getQueryData());
        return super.save(entity);
    }

    @Override
    public BaseResultList<StreamProcessorFilter> find(FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria) {
        return withPipelineName(super.find(findStreamProcessorFilterCriteria));
    }

    @Override
    public Class<StreamProcessorFilter> getEntityClass() {
        return StreamProcessorFilter.class;
    }

    @Override
    public void addFindStreamCriteria(final StreamProcessor streamProcessor,
                                      final int priority,
                                      final QueryData queryData) {
        security.secure(permission(), () -> {
            entityManagerSupport.transaction(entityManager -> {
                StreamProcessorFilter filter = new StreamProcessorFilter();
                // Blank tracker
                filter.setStreamProcessorFilterTracker(new StreamProcessorFilterTracker());
                filter.setPriority(priority);
                filter.setStreamProcessor(streamProcessor);
                filter.setQueryData(queryData);
                filter.setEnabled(true);
                filter = marshaller.marshal(filter);
                // Save initial tracker
                getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
                getEntityManager().flush();
                save(filter);
            });
        });
    }

    @Override
    public StreamProcessorFilter createNewFilter(final DocRef pipelineRef,
                                                 final QueryData queryData,
                                                 final boolean enabled,
                                                 final int priority) {
        return security.secureResult(permission(), () -> entityManagerSupport.transactionResult(entityManager -> {
            // First see if we can find a stream processor for this pipeline.
            final FindStreamProcessorCriteria findStreamProcessorCriteria = new FindStreamProcessorCriteria(pipelineRef);
            final List<StreamProcessor> list = streamProcessorService.find(findStreamProcessorCriteria);
            StreamProcessor processor = null;
            if (list == null || list.size() == 0) {
                // We couldn't find one so create a new one.
                processor = new StreamProcessor(pipelineRef);
                processor.setEnabled(enabled);
                processor = streamProcessorService.save(processor);
            } else {
                processor = list.get(0);
            }

            StreamProcessorFilter filter = new StreamProcessorFilter();
            // Blank tracker
            filter.setEnabled(enabled);
            filter.setPriority(priority);
            filter.setStreamProcessorFilterTracker(new StreamProcessorFilterTracker());
            filter.setStreamProcessor(processor);
            filter.setQueryData(queryData);
            filter = marshaller.marshal(filter);
            // Save initial tracker
            getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
            getEntityManager().flush();
            filter = save(filter);
            filter = marshaller.unmarshal(filter);

            return filter;
        }));
    }

    @Override
    public FindStreamProcessorFilterCriteria createCriteria() {
        return new FindStreamProcessorFilterCriteria();
    }

    @Override
    public Boolean delete(final StreamProcessorFilter entity) {
        return security.secureResult(permission(), () -> {
            if (Boolean.TRUE.equals(super.delete(entity))) {
                return getEntityManager().deleteEntity(entity.getStreamProcessorFilterTracker());
            }
            return Boolean.FALSE;
        });
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items,
                               final FindStreamProcessorFilterCriteria criteria) {
        CriteriaLoggingUtil.appendStringTerm(items, "pipelineName", criteria.getPipelineNameFilter());
        CriteriaLoggingUtil.appendRangeTerm(items, "priorityRange", criteria.getPriorityRange());
        CriteriaLoggingUtil.appendRangeTerm(items, "lastPollPeriod", criteria.getLastPollPeriod());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorIdSet", criteria.getStreamProcessorIdSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineSet());
        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorEnabled", criteria.getStreamProcessorEnabled());
        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorFilterEnabled",
                criteria.getStreamProcessorFilterEnabled());
        CriteriaLoggingUtil.appendStringTerm(items, "createUser", criteria.getCreateUser());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<StreamProcessorFilter, FindStreamProcessorFilterCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new StreamProcessorFilterQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    }


    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindStreamTaskCriteria.FIELD_PRIORITY, "PRIORITY_1", "priority");
    }

    /**
     * Sets the pipeline name on the StreamProcessorFilter's StreamProcessors.
     *
     * @param streamProcessorFilters
     * @return The StreamProcessorFilters with the pipeline name resolved
     */
    private BaseResultList<StreamProcessorFilter> withPipelineName(BaseResultList<StreamProcessorFilter> streamProcessorFilters){
        final Map<DocRef, Optional<Object>> uuids = new HashMap<>();
        for (StreamProcessorFilter streamProcessorFilter : streamProcessorFilters) {
            StreamProcessor streamProcessor = streamProcessorService.load(streamProcessorFilter.getStreamProcessor());
            streamProcessorFilter.setStreamProcessor(streamProcessor);
            String pipelineUuid = streamProcessor.getPipelineUuid();
            if (pipelineUuid != null) {
                uuids
                        .computeIfAbsent(
                                new DocRef(PipelineDoc.DOCUMENT_TYPE, pipelineUuid),
                                innerKey -> Optional.ofNullable(pipelineStore.readDocument(innerKey)))
                        .ifPresent(obj ->
                                streamProcessor.setPipelineName(((PipelineDoc) obj).getName()));
            }
        }
        return streamProcessorFilters;
    }

    private static class StreamProcessorFilterQueryAppender extends QueryAppender<StreamProcessorFilter, FindStreamProcessorFilterCriteria> {
        private final StreamProcessorFilterMarshaller marshaller;

        StreamProcessorFilterQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            marshaller = new StreamProcessorFilterMarshaller();
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains(StreamProcessor.ENTITY_TYPE) || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
                    sql.append(" INNER JOIN FETCH ");
                    sql.append(alias);
                    sql.append(".streamProcessor as sp");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
                                           final FindStreamProcessorFilterCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            sql.appendRangeQuery(alias + ".priority", criteria.getPriorityRange());
            sql.appendValueQuery(alias + ".enabled", criteria.getStreamProcessorFilterEnabled());
            sql.appendValueQuery(alias + ".createUser", criteria.getCreateUser());

            sql.appendEntityIdSetQuery(alias + ".streamProcessor", criteria.getStreamProcessorIdSet());
            sql.appendValueQuery(alias + ".streamProcessor.enabled", criteria.getStreamProcessorEnabled());
            sql.appendDocRefSetQuery(alias + ".streamProcessor.pipelineUuid", criteria.getPipelineSet());

            sql.appendRangeQuery(alias + ".streamProcessorFilterTracker.lastPollMs",
                    criteria.getLastPollPeriod());
            StringCriteria statusStringCriteria = new StringCriteria(criteria.getStatus(),
                    StringCriteria.MatchStyle.WildEnd);
            if (criteria.getStatus() == "") {
                statusStringCriteria.setMatchNull(true);
            }
            sql.appendValueQuery(alias + ".streamProcessorFilterTracker.status", statusStringCriteria);
        }

        @Override
        protected void preSave(final StreamProcessorFilter entity) {
            super.preSave(entity);
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final StreamProcessorFilter entity) {
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}
