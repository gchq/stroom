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

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.UUID;

@Singleton
class ProcessorFilterServiceImpl implements ProcessorFilterService {
    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;

    private final ProcessorService processorService;
    private final ProcessorFilterDao processorFilterDao;
    private final SecurityContext securityContext;

    @Inject
    ProcessorFilterServiceImpl(final ProcessorService processorService,
                               final ProcessorFilterDao processorFilterDao,
                               final SecurityContext securityContext) {
        this.processorService = processorService;
        this.processorFilterDao = processorFilterDao;
        this.securityContext = securityContext;
    }

    @Override
    public ProcessorFilter create(final DocRef pipelineRef,
                                  final QueryData queryData,
                                  final int priority,
                                  final boolean enabled) {
        final Processor processor = processorService.create(pipelineRef, enabled);
        return create(processor, queryData, priority, enabled);
    }

    @Override
    public ProcessorFilter create(final Processor processor,
                                  final QueryData queryData,
                                  final int priority,
                                  final boolean enabled) {
        // now create the filter and tracker
        final ProcessorFilter processorFilter = new ProcessorFilter();
        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
        // Blank tracker
        processorFilter.setEnabled(enabled);
        processorFilter.setPriority(priority);
        processorFilter.setProcessor(processor);
        processorFilter.setQueryData(queryData);
        return create(processorFilter);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        if (processorFilter.getUuid() == null) {
            processorFilter.setUuid(UUID.randomUUID().toString());
        }

        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.create(processorFilter));
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.fetch(id));
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        if (processorFilter.getUuid() == null) {
            processorFilter.setUuid(UUID.randomUUID().toString());
        }

        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.update(processorFilter));
    }

    @Override
    public boolean delete(final int id) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.delete(id));
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
//        return securityContext.secureResult(permission(), () ->
//                processorFilterDao.create(processorFilter));
//    }

//    @Override
//    public ProcessorFilter update(final ProcessorFilter processorFilter) {
//        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
//        return securityContext.secureResult(permission(), () ->
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
//        return securityContext.secureResult(permission(), () ->
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
//        return securityContext.secureResult(permission(), () ->
//                processorFilterDao.fetch(id));
//    }

//    @Override
//    public Class<ProcessorFilter> getEntityClass() {
//        return ProcessorFilter.class;
//    }

    @Override
    public BaseResultList<ProcessorFilter> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.find(criteria));
    }

//    @Override
//    public ProcessorFilter createFilter(final Processor streamProcessor,
//                                        final QueryData queryData,
//                                        final boolean enabled,
//                                        final int priority) {
//
////        securityContext.secure(permission(), () -> entityManagerSupport.transaction(entityManager -> {
////            ProcessorFilter filter = new ProcessorFilter();
////            // Blank tracker
////            filter.setProcessorFilterTracker(new ProcessorFilterTracker());
////            filter.setPriority(priority);
////            filter.setProcessor(streamProcessor);
////            filter.setQueryData(queryData);
////            filter.setEnabled(true);
////            filter = marshaller.marshal(filter);
////            // Save initial tracker
////            getEntityManager().saveEntity(filter.getProcessorFilterTracker());
////            getEntityManager().flush();
////            save(filter);
////        }));
//        return securityContext.secureResult(permission(), () ->
//                processorFilterDao.createFilter(streamProcessor, queryData, enabled, priority));
//    }
//
//
//
//
//
//













































//    @Override
//    public FindProcessorFilterCriteria createCriteria() {
//        return new FindProcessorFilterCriteria();
//    }

//    @Override
//    public Boolean delete(final ProcessorFilter entity) {
//        return securityContext.secureResult(permission(), () -> {
//            if (Boolean.TRUE.equals(super.delete(entity))) {
//                return getEntityManager().deleteEntity(entity.getProcessorFilterTracker());
//            }
//            return Boolean.FALSE;
//        });
//    }

//    public void appendCriteria(final List<BaseAdvancedQueryItem> items,
//                               final FindProcessorFilterCriteria criteria) {
//        CriteriaLoggingUtil.appendRangeTerm(items, "priorityRange", criteria.getPriorityRange());
//        CriteriaLoggingUtil.appendRangeTerm(items, "lastPollPeriod", criteria.getLastPollPeriod());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorIdSet", criteria.getStreamProcessorIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineUuidCriteria());
//        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorEnabled", criteria.getProcessorEnabled());
//        CriteriaLoggingUtil.appendBooleanTerm(items, "processorFilterEnabled",
//                criteria.getProcessorFilterEnabled());
//        CriteriaLoggingUtil.appendStringTerm(items, "createUser", criteria.getCreateUser());
//        super.appendCriteria(items, criteria);
//    }

//    @Override
//    protected QueryAppender<ProcessorFilter, FindProcessorFilterCriteria> createQueryAppender(final StroomEntityManager entityManager) {
//        return new ProcessorFilterQueryAppender(entityManager);
//    }
//
//    protected String permission() {
//        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
//    }

//    private static class ProcessorFilterQueryAppender extends QueryAppender<ProcessorFilter, FindProcessorFilterCriteria> {
//        private final ProcessorFilterMarshaller marshaller;
//
//        ProcessorFilterQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//            marshaller = new ProcessorFilterMarshaller();
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
//                                           final FindProcessorFilterCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//            sql.appendRangeQuery(alias + ".priority", criteria.getPriorityRange());
//
//            sql.appendValueQuery(alias + ".streamProcessor.enabled", criteria.getProcessorEnabled());
//
//            sql.appendValueQuery(alias + ".enabled", criteria.getProcessorFilterEnabled());
//
//            sql.appendRangeQuery(alias + ".processorFilterTracker.lastPollMs", criteria.getLastPollPeriod());
//
//            sql.appendDocRefSetQuery(alias + ".streamProcessor.pipelineUuid", criteria.getPipelineUuidCriteria());
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
