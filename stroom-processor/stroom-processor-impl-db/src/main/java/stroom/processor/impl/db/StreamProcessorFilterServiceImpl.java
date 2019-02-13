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

package stroom.processor.impl.db;


import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.processor.StreamProcessorFilterService;
import stroom.processor.StreamProcessorService;
import stroom.processor.impl.db.dao.ProcessorFilterDao;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class StreamProcessorFilterServiceImpl implements StreamProcessorFilterService {
//        extends SystemEntityServiceImpl<ProcessorFilter, FindStreamProcessorFilterCriteria>

    private final Security security;
    private final SecurityContext securityContext;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterMarshaller marshaller;
    private final ProcessorFilterDao processorFilterDao;

    @Inject
    StreamProcessorFilterServiceImpl(final Security security,
                                     final SecurityContext securityContext,
                                     final StreamProcessorService streamProcessorService,
                                     final ProcessorFilterDao processorFilterDao) {
        this.security = security;
        this.securityContext = securityContext;
        this.streamProcessorService = streamProcessorService;
        this.processorFilterDao = processorFilterDao;
        this.marshaller = new StreamProcessorFilterMarshaller();
    }

//    /**
//     * Creates the passes record object in the persistence implementation
//     *
//     * @param processorFilter Object to persist.
//     * @return The persisted object including any changes such as auto IDs
//     */
//    @Override
//    public ProcessorFilter create(final ProcessorFilter processorFilter) {
//        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
//        return security.secureResult(permission(), () ->
//                processorFilterDao.create(processorFilter));
//    }

//    @Override
//    public ProcessorFilter update(final ProcessorFilter processorFilter) {
//        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
//        return security.secureResult(permission(), () ->
//                processorFilterDao.update(processorFilter));
//    }

//    /**
//     * Delete the entity associated with the passed id value.
//     *
//     * @param id The unique identifier for the entity to delete.
//     * @return True if the entity was deleted. False if the id doesn't exist.
//     */
//    @Override
//    public boolean delete(final int id) {
//        return security.secureResult(permission(), () ->
//                processorFilterDao.delete(id));
//    }

//    /**
//     * Fetch a record from the persistence implementation using its unique id value.
//     *
//     * @param id The id to uniquely identify the required record with
//     * @return The record associated with the id in the database, if it exists.
//     */
//    @Override
//    public Optional<ProcessorFilter> fetch(final int id) {
//        return security.secureResult(permission(), () ->
//                processorFilterDao.fetch(id));
//    }

//    @Override
//    public Class<ProcessorFilter> getEntityClass() {
//        return ProcessorFilter.class;
//    }

    @Override
    public BaseResultList<ProcessorFilter> find(final FindStreamProcessorFilterCriteria criteria) {
        return security.secureResult(permission(), () ->
                processorFilterDao.find(criteria));
    }

    @Override
    public ProcessorFilter createFilter(final Processor streamProcessor,
                                        final QueryData queryData,
                                        final boolean enabled,
                                        final int priority) {

//        security.secure(permission(), () -> entityManagerSupport.transaction(entityManager -> {
//            ProcessorFilter filter = new ProcessorFilter();
//            // Blank tracker
//            filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
//            filter.setPriority(priority);
//            filter.setStreamProcessor(streamProcessor);
//            filter.setQueryData(queryData);
//            filter.setEnabled(true);
//            filter = marshaller.marshal(filter);
//            // Save initial tracker
//            getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
//            getEntityManager().flush();
//            save(filter);
//        }));
        return security.secureResult(permission(), () ->
                processorFilterDao.createFilter(streamProcessor, queryData, enabled, priority));
    }

    @Override
    public ProcessorFilter createFilter(final DocRef pipelineRef,
                                        final QueryData queryData,
                                        final boolean enabled,
                                        final int priority) {
        return security.secureResult(permission(), () ->
                processorFilterDao.createFilter(pipelineRef, queryData, enabled, priority));
    }

//    @Override
//    public FindStreamProcessorFilterCriteria createCriteria() {
//        return new FindStreamProcessorFilterCriteria();
//    }

//    @Override
//    public Boolean delete(final ProcessorFilter entity) {
//        return security.secureResult(permission(), () -> {
//            if (Boolean.TRUE.equals(super.delete(entity))) {
//                return getEntityManager().deleteEntity(entity.getStreamProcessorFilterTracker());
//            }
//            return Boolean.FALSE;
//        });
//    }

//    public void appendCriteria(final List<BaseAdvancedQueryItem> items,
//                               final FindStreamProcessorFilterCriteria criteria) {
//        CriteriaLoggingUtil.appendRangeTerm(items, "priorityRange", criteria.getPriorityRange());
//        CriteriaLoggingUtil.appendRangeTerm(items, "lastPollPeriod", criteria.getLastPollPeriod());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorIdSet", criteria.getStreamProcessorIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineSet());
//        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorEnabled", criteria.getStreamProcessorEnabled());
//        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorFilterEnabled",
//                criteria.getStreamProcessorFilterEnabled());
//        CriteriaLoggingUtil.appendStringTerm(items, "createUser", criteria.getCreateUser());
//        super.appendCriteria(items, criteria);
//    }

//    @Override
//    protected QueryAppender<ProcessorFilter, FindStreamProcessorFilterCriteria> createQueryAppender(final StroomEntityManager entityManager) {
//        return new StreamProcessorFilterQueryAppender(entityManager);
//    }

    protected String permission() {
        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    }

//    private static class StreamProcessorFilterQueryAppender extends QueryAppender<ProcessorFilter, FindStreamProcessorFilterCriteria> {
//        private final StreamProcessorFilterMarshaller marshaller;
//
//        StreamProcessorFilterQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//            marshaller = new StreamProcessorFilterMarshaller();
//        }
//
//        @Override
//        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
//            super.appendBasicJoin(sql, alias, fetchSet);
//            if (fetchSet != null) {
//                if (fetchSet.contains(Processor.ENTITY_TYPE) || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
//                    sql.append(" INNER JOIN FETCH ");
//                    sql.append(alias);
//                    sql.append(".streamProcessor as sp");
//                }
//                // TODO this no longer works
////                if (fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
////                    sql.append(" INNER JOIN FETCH ");
////                    sql.append("sp.pipelineName");
////                }
//            }
//        }
//
//        @Override
//        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
//                                           final FindStreamProcessorFilterCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//            sql.appendRangeQuery(alias + ".priority", criteria.getPriorityRange());
//
//            sql.appendValueQuery(alias + ".streamProcessor.enabled", criteria.getStreamProcessorEnabled());
//
//            sql.appendValueQuery(alias + ".enabled", criteria.getStreamProcessorFilterEnabled());
//
//            sql.appendRangeQuery(alias + ".streamProcessorFilterTracker.lastPollMs", criteria.getLastPollPeriod());
//
//            sql.appendDocRefSetQuery(alias + ".streamProcessor.pipelineUuid", criteria.getPipelineSet());
//
//            sql.appendEntityIdSetQuery(alias + ".streamProcessor", criteria.getStreamProcessorIdSet());
//
//            sql.appendValueQuery(alias + ".createUser", criteria.getCreateUser());
//        }
//
//        @Override
//        protected void preSave(final ProcessorFilter entity) {
//            super.preSave(entity);
//            marshaller.marshal(entity);
//        }
//
//        @Override
//        protected void postLoad(final ProcessorFilter entity) {
//            marshaller.unmarshal(entity);
//            super.postLoad(entity);
//        }
//    }
}
