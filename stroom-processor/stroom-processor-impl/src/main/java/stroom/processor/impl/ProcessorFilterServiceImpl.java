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

import com.google.common.base.Strings;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.api.ExplorerService;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorDataSource;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ProcessorRow;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.Expander;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
class ProcessorFilterServiceImpl implements ProcessorFilterService {
    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    private static final int MAX_STREAM_TO_REPROCESS = 1000;

    private final ProcessorService processorService;
    private final ProcessorFilterDao processorFilterDao;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final ExplorerService explorerService;

    @Inject
    ProcessorFilterServiceImpl(final ProcessorService processorService,
                               final ProcessorFilterDao processorFilterDao,
                               final MetaService metaService,
                               final SecurityContext securityContext,
                               final ExplorerService explorerService) {
        this.processorService = processorService;
        this.processorFilterDao = processorFilterDao;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.explorerService = explorerService;
    }

    @Override
    public ProcessorFilter create(final DocRef pipelineRef,
                                  final QueryData queryData,
                                  final int priority,
                                  final boolean enabled) {
        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(PipelineDoc.DOCUMENT_TYPE, pipelineRef.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException("You do not have permission to create this processor filter");
        }

        final Processor processor = processorService.create(pipelineRef, enabled);
        return create(processor, queryData, priority, enabled);
    }

    @Override
    public ProcessorFilter create(final Processor processor,
                                  final QueryData queryData,
                                  final int priority,
                                  final boolean enabled) {
        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(PipelineDoc.DOCUMENT_TYPE, processor.getPipelineUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException("You do not have permission to create this processor filter");
        }

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
        // Check the user has update permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(PipelineDoc.DOCUMENT_TYPE, processorFilter.getProcessor().getPipelineUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException("You do not have permission to update this processor filter");
        }

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

    @Override
    public void setPriority(final Integer id, final Integer priority) {
        fetch(id).ifPresent(processorFilter -> {
            processorFilter.setPriority(priority);
            update(processorFilter);
        });
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        fetch(id).ifPresent(processorFilter -> {
            processorFilter.setEnabled(enabled);
            update(processorFilter);
        });
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
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.find(criteria));
    }

    // TODO : The following method combines results from the processor and processor filter services so should possibly
    //  be in another class that controls the collaboration.
    @Override
    public ProcessorListRowResultPage find(final FetchProcessorRequest request) {
        return securityContext.secureResult(PERMISSION, () -> {
            final List<ProcessorListRow> values = new ArrayList<>();

            final ExpressionCriteria criteria = new ExpressionCriteria(request.getExpression());
            final ExpressionCriteria criteriaRoot = new ExpressionCriteria(request.getExpression());
//            if (action.getPipeline() != null) {
//                criteria.obtainPipelineUuidCriteria().setString(action.getPipeline().getUuid());
//                criteriaRoot.obtainPipelineUuidCriteria().setString(action.getPipeline().getUuid());
//            }

            // If the user is not an admin then only show them filters that were created by them.
            if (!securityContext.isAdmin()) {
                final ExpressionOperator.Builder builder = new Builder(Op.AND)
                        .addTerm(ProcessorDataSource.CREATE_USER, Condition.EQUALS, securityContext.getUserId())
                        .addOperator(criteria.getExpression());
                criteria.setExpression(builder.build());
            }

            final ResultPage<Processor> streamProcessors = processorService.find(criteriaRoot);

            final ResultPage<ProcessorFilter> processorFilters = find(criteria);

            // Get unique processors.
            final Set<Processor> processors = new HashSet<>(streamProcessors.getValues());

            final List<Processor> sorted = new ArrayList<>(processors);
            sorted.sort((o1, o2) -> {
                if (o1.getPipelineUuid() != null && o2.getPipelineUuid() != null) {
                    return o1.getPipelineUuid().compareTo(o2.getPipelineUuid());
                }
                if (o1.getPipelineUuid() != null) {
                    return -1;
                }
                if (o2.getPipelineUuid() != null) {
                    return 1;
                }
                return o1.getId().compareTo(o2.getId());
            });

            for (final Processor processor : sorted) {
                final Expander processorExpander = new Expander(0, false, false);
                final ProcessorRow processorRow = new ProcessorRow(processorExpander,
                        processor);
                values.add(processorRow);

                // If the job row is open then add child rows.
                if (request.getExpandedRows() == null || request.isRowExpanded(processorRow)) {
                    processorExpander.setExpanded(true);

                    // Add filters.
                    for (final ProcessorFilter processorFilter : processorFilters.getValues()) {
                        if (processor.equals(processorFilter.getProcessor())) {
                            // Decorate the expression with resolved dictionaries etc.
                            final QueryData queryData = processorFilter.getQueryData();
                            if (queryData != null && queryData.getExpression() != null) {
                                queryData.setExpression(decorate(queryData.getExpression()));
                            }

                            final ProcessorFilterRow processorFilterRow = new ProcessorFilterRow(processorFilter);
                            values.add(processorFilterRow);
                        }
                    }
                }
            }

            return new ProcessorListRowResultPage(values, ResultPage.createUnboundedPageResponse(values));
        });
    }

    private ExpressionOperator decorate(final ExpressionOperator operator) {
        final ExpressionOperator.Builder builder = new Builder()
                .op(operator.getOp())
                .enabled(operator.isEnabled());

        if (operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child instanceof ExpressionOperator) {
                    builder.addOperator(decorate((ExpressionOperator) child));

                } else if (child instanceof ExpressionTerm) {
                    ExpressionTerm term = (ExpressionTerm) child;
                    DocRef docRef = term.getDocRef();

                    if (docRef != null) {
                        final DocRefInfo docRefInfo = explorerService.info(docRef);
                        if (docRefInfo != null) {
                            term = new ExpressionTerm.Builder()
                                    .enabled(term.isEnabled())
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

                    builder.addTerm(term);
                }
            }
        }

        return builder.build();
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final FindMetaCriteria criteria) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROCESSORS_PERMISSION, () -> {
            final List<ReprocessDataInfo> info = new ArrayList<>();

            try {
                // We only want 1000 streams to be
                // reprocessed at a maximum.
                criteria.obtainPageRequest().setOffset(0L);
                criteria.obtainPageRequest().setLength(MAX_STREAM_TO_REPROCESS);

                final ResultPage<Meta> metaList = metaService.find(criteria);

                if (!metaList.isExact()) {
                    info.add(new ReprocessDataInfo(Severity.ERROR, "Results exceed " + MAX_STREAM_TO_REPROCESS
                            + " configure a pipeline processor for large data sets", null));

                } else {
                    int skippingCount = 0;
                    final StringBuilder unableListSB = new StringBuilder();
                    final StringBuilder submittedListSB = new StringBuilder();

                    final Map<Processor, CriteriaSet<Long>> streamToProcessorSet = new HashMap<>();

                    for (final Meta meta : metaList.getValues()) {
                        // We can only reprocess streams that have a stream processor and a parent stream id.
                        if (meta.getProcessorUuid() != null && meta.getParentMetaId() != null) {
                            final ExpressionCriteria findProcessorCriteria = new ExpressionCriteria(new ExpressionOperator.Builder()
                                    .addTerm(ProcessorDataSource.PIPELINE, Condition.EQUALS, new DocRef(PipelineDoc.DOCUMENT_TYPE, meta.getPipelineUuid()))
                                    .build());
//                            findProcessorCriteria.obtainPipelineUuidCriteria().setString(meta.getPipelineUuid());
                            final Processor processor = processorService.find(findProcessorCriteria).getFirst();
                            streamToProcessorSet.computeIfAbsent(processor, k -> new CriteriaSet<>()).add(meta.getParentMetaId());
                        } else {
                            skippingCount++;
                        }
                    }

                    final List<Processor> list = new ArrayList<>(streamToProcessorSet.keySet());
                    list.sort(Comparator.comparing(Processor::getPipelineUuid));

                    for (final Processor streamProcessor : list) {
                        final QueryData queryData = new QueryData();
                        final ExpressionOperator.Builder operator = new ExpressionOperator.Builder(ExpressionOperator.Op.AND);

                        final CriteriaSet<Long> streamIdSet = streamToProcessorSet.get(streamProcessor);
                        if (streamIdSet != null && streamIdSet.size() > 0) {
                            if (streamIdSet.size() == 1) {
                                operator.addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, streamIdSet.getSingleItem());
                            } else {
                                final ExpressionOperator.Builder streamIdTerms = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);
                                streamIdSet.forEach(streamId -> streamIdTerms.addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, streamId));
                                operator.addOperator(streamIdTerms.build());
                            }
                        }

                        queryData.setDataSource(MetaFields.STREAM_STORE_DOC_REF);
                        queryData.setExpression(operator.build());

                        if (!streamProcessor.isEnabled()) {
                            unableListSB.append(streamProcessor.getPipelineUuid());
                            unableListSB.append("\n");

                        } else {
                            final String padded = Strings.padEnd(streamProcessor.getPipelineUuid(), 40, ' ');
                            submittedListSB.append(padded);
                            submittedListSB.append("\t");
                            submittedListSB.append(streamIdSet.size());
                            submittedListSB.append(" streams\n");

                            create(streamProcessor, queryData, 10, true);
                        }
                    }

                    if (skippingCount > 0) {
                        info.add(new ReprocessDataInfo(Severity.INFO,
                                "Skipping " + skippingCount + " streams that are not a result of processing", null));
                    }

                    final String unableList = unableListSB.toString().trim();
                    if (unableList.length() > 0) {
                        info.add(new ReprocessDataInfo(Severity.WARNING,
                                "Unable to reprocess all streams as some pipelines are not enabled", unableList));
                    }

                    final String submittedList = submittedListSB.toString().trim();
                    if (submittedList.length() > 0) {
                        info.add(new ReprocessDataInfo(Severity.INFO, "Created new processor filters to reprocess streams",
                                submittedList));
                    }
                }
            } catch (final RuntimeException e) {
                info.add(new ReprocessDataInfo(Severity.ERROR, e.getMessage(), null));
            }

            return info;
        });
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
