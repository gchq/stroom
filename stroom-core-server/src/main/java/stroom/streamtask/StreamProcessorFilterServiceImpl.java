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
import stroom.docref.DocRef;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.util.HqlBuilder;
import stroom.persist.EntityManagerSupport;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTracker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
class StreamProcessorFilterServiceImpl
        extends SystemEntityServiceImpl<ProcessorFilter, FindStreamProcessorFilterCriteria>
        implements StreamProcessorFilterService {
    private final Security security;
    private final EntityManagerSupport entityManagerSupport;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterMarshaller marshaller;

    @Inject
    StreamProcessorFilterServiceImpl(final StroomEntityManager entityManager,
                                     final Security security,
                                     final EntityManagerSupport entityManagerSupport,
                                     final StreamProcessorService streamProcessorService) {
        super(entityManager, security);
        this.security = security;
        this.entityManagerSupport = entityManagerSupport;
        this.streamProcessorService = streamProcessorService;
        this.marshaller = new StreamProcessorFilterMarshaller();
    }

    @Override
    public ProcessorFilter save(final ProcessorFilter entity) {
        return super.save(entity);
    }

    @Override
    public Class<ProcessorFilter> getEntityClass() {
        return ProcessorFilter.class;
    }

    @Override
    public void addFindStreamCriteria(final Processor streamProcessor,
                                      final int priority,
                                      final QueryData queryData) {
        security.secure(permission(), () -> entityManagerSupport.transaction(entityManager -> {
            ProcessorFilter filter = new ProcessorFilter();
            // Blank tracker
            filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
            filter.setPriority(priority);
            filter.setStreamProcessor(streamProcessor);
            filter.setQueryData(queryData);
            filter.setEnabled(true);
            filter = marshaller.marshal(filter);
            // Save initial tracker
            getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
            getEntityManager().flush();
            save(filter);
        }));
    }

    @Override
    public ProcessorFilter createNewFilter(final DocRef pipelineRef,
                                           final QueryData queryData,
                                           final boolean enabled,
                                           final int priority) {
        return security.secureResult(permission(), () -> entityManagerSupport.transactionResult(entityManager -> {
            // First see if we can find a stream processor for this pipeline.
            final FindStreamProcessorCriteria findStreamProcessorCriteria = new FindStreamProcessorCriteria(pipelineRef);
            final List<Processor> list = streamProcessorService.find(findStreamProcessorCriteria);
            Processor processor;
            if (list == null || list.size() == 0) {
                // We couldn't find one so create a new one.
                processor = new Processor(pipelineRef);
                processor.setEnabled(enabled);
                processor = streamProcessorService.save(processor);
            } else {
                processor = list.get(0);
            }

            ProcessorFilter filter = new ProcessorFilter();
            // Blank tracker
            filter.setEnabled(enabled);
            filter.setPriority(priority);
            filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
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
    public Boolean delete(final ProcessorFilter entity) {
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
    protected QueryAppender<ProcessorFilter, FindStreamProcessorFilterCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new StreamProcessorFilterQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    }

    private static class StreamProcessorFilterQueryAppender extends QueryAppender<ProcessorFilter, FindStreamProcessorFilterCriteria> {
        private final StreamProcessorFilterMarshaller marshaller;

        StreamProcessorFilterQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            marshaller = new StreamProcessorFilterMarshaller();
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains(Processor.ENTITY_TYPE) || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
                    sql.append(" INNER JOIN FETCH ");
                    sql.append(alias);
                    sql.append(".streamProcessor as sp");
                }
                if (fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
                    sql.append(" INNER JOIN FETCH ");
                    sql.append("sp.pipeline");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
                                           final FindStreamProcessorFilterCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendRangeQuery(alias + ".priority", criteria.getPriorityRange());

            sql.appendValueQuery(alias + ".streamProcessor.enabled", criteria.getStreamProcessorEnabled());

            sql.appendValueQuery(alias + ".enabled", criteria.getStreamProcessorFilterEnabled());

            sql.appendRangeQuery(alias + ".streamProcessorFilterTracker.lastPollMs", criteria.getLastPollPeriod());

            sql.appendDocRefSetQuery(alias + ".streamProcessor.pipelineUuid", criteria.getPipelineSet());

            sql.appendEntityIdSetQuery(alias + ".streamProcessor", criteria.getStreamProcessorIdSet());

            sql.appendValueQuery(alias + ".createUser", criteria.getCreateUser());
        }

        @Override
        protected void preSave(final ProcessorFilter entity) {
            super.preSave(entity);
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final ProcessorFilter entity) {
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}
