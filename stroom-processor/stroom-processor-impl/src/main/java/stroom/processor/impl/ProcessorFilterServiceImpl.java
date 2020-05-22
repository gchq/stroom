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
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorDataSource;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
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
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Expander;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
class ProcessorFilterServiceImpl implements ProcessorFilterService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterServiceImpl.class);

    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;

    private final ProcessorService processorService;
    private final ProcessorFilterDao processorFilterDao;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final DocRefInfoService docRefInfoService;

    @Inject
    ProcessorFilterServiceImpl(final ProcessorService processorService,
                               final ProcessorFilterDao processorFilterDao,
                               final MetaService metaService,
                               final SecurityContext securityContext,
                               final DocRefInfoService docRefInfoService) {
        this.processorService = processorService;
        this.processorFilterDao = processorFilterDao;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    public ProcessorFilter create(final DocRef pipelineRef,
                                  final QueryData queryData,
                                  final int priority,
                                  final boolean autoPriority,
                                  final boolean enabled) {
        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(pipelineRef.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to create this processor filter");
        }

        final Processor processor = processorService.create(pipelineRef, enabled);
        return create(processor, queryData, priority, autoPriority, enabled);
    }

    @Override
    public ProcessorFilter create(final Processor processor,
                                  final QueryData queryData,
                                  final int priority,
                                  final boolean autoPriority,
                                  final boolean enabled) {
        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(processor.getPipelineUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to create this processor filter");
        }

        // If we are using auto priority then try and get a priority.
        final int calculatedPriority = getAutoPriority(processor, priority, autoPriority);

        // now create the filter and tracker
        final ProcessorFilter processorFilter = new ProcessorFilter();
        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
        // Blank tracker
        processorFilter.setEnabled(enabled);
        processorFilter.setPriority(calculatedPriority);
        processorFilter.setProcessor(processor);
        processorFilter.setQueryData(queryData);
        return create(processorFilter);
    }

    @Override
    public ProcessorFilter importFilter(final Processor processor,
                                        final DocRef processorFilterDocRef,
                                        final QueryData queryData,
                                        final int priority,
                                        final boolean autoPriority,
                                        final boolean reprocess,
                                        final boolean enabled,
                                        final Long minMetaCreateMs) {
        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(processor.getPipelineUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to create this processor filter");
        }

        // If we are using auto priority then try and get a priority.
        final int calculatedPriority = getAutoPriority(processor, priority, autoPriority);

        if (queryData != null && queryData.getDataSource() == null) {
            queryData.setDataSource(MetaFields.STREAM_STORE_DOC_REF);
        }

        // now create the filter and tracker
        final ProcessorFilter processorFilter = new ProcessorFilter();
        AuditUtil.stamp(securityContext.getUserId(), processorFilter);
        // Blank tracker
        processorFilter.setReprocess(reprocess);
        processorFilter.setEnabled(enabled);
        processorFilter.setPriority(calculatedPriority);
        processorFilter.setProcessor(processor);
        processorFilter.setQueryData(queryData);

        if (processorFilterDocRef != null) {
            processorFilter.setUuid(processorFilterDocRef.getUuid());
        }

        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.create(processorFilter, minMetaCreateMs, null));
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.create(ensureValid(processorFilter)));
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.fetch(id));
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        // Check the user has update permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(
                processorFilter.getProcessor().getPipelineUuid(),
                DocumentPermissionNames.UPDATE)) {

            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to update this processor filter");
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
                processorFilterDao.logicalDelete(id));
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

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () ->
                processorFilterDao.find(criteria));
    }

    // TODO : The following method combines results from the processor and processor filter services so should possibly
    //  be in another class that controls the collaboration.
    @Override
    public ResultPage<ProcessorListRow> find(final FetchProcessorRequest request) {
        return securityContext.secureResult(PERMISSION, () -> {
            final List<ProcessorListRow> values = new ArrayList<>();

            final ExpressionCriteria criteria = new ExpressionCriteria(request.getExpression());
            final ExpressionCriteria criteriaRoot = new ExpressionCriteria(request.getExpression());

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

                updatePipelineName(processor);

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

                            if (processorFilter.getPipelineName() == null) {
                                if (processor.getPipelineName() == null) {
                                    updatePipelineName(processor);
                                }
                                processorFilter.setPipelineName(processor.getPipelineName());
                            }

                            final ProcessorFilterRow processorFilterRow = new ProcessorFilterRow(processorFilter);
                            values.add(processorFilterRow);
                        }
                    }
                }
            }

            return ResultPage.createUnboundedList(values);
        });
    }

    private void updatePipelineName(final Processor processor) {
        if (processor.getPipelineName() == null && processor.getPipelineUuid() != null) {
            processor.setPipelineName(docRefInfoService
                    .name(new DocRef(PipelineDoc.DOCUMENT_TYPE, processor.getPipelineUuid()))
                    .orElseGet(() -> {
                        LOGGER.warn("Unable to find Pipeline " +
                                processor.getPipelineUuid() +
                                " associated with Processor " +
                                processor.getUuid() +
                                " (id: " +
                                processor.getId() +
                                ")" +
                                " Has it been deleted?");
                        return null;
                    }));
        }
    }

    @Override
    public ResultPage<ProcessorFilter> find(final DocRef pipelineDocRef) {
        if (pipelineDocRef == null) {
            throw new IllegalArgumentException("Supplied pipeline reference cannot be null");
        }

        if (!PipelineDoc.DOCUMENT_TYPE.equals(pipelineDocRef.getType())) {
            throw new IllegalArgumentException("Supplied pipeline reference cannot be of type " +
                    pipelineDocRef.getType());
        }

        // First try to find the associated processors
        final ExpressionOperator processorExpression = new ExpressionOperator.Builder()
                .addTerm(ProcessorDataSource.PIPELINE, Condition.IS_DOC_REF, pipelineDocRef).build();
        ResultPage<Processor> processorResultPage = processorService.find(new ExpressionCriteria(processorExpression));
        if (processorResultPage.size() == 0)
            return new ResultPage<>(new ArrayList<>());

        final ArrayList<ProcessorFilter> filters = new ArrayList<>();
        // Now find all the processor filters
        for (Processor processor : processorResultPage.getValues()) {
            final ExpressionOperator filterExpression = new ExpressionOperator.Builder()
                    .addTerm(ProcessorFilterFields.PROCESSOR_ID, ExpressionTerm.Condition.EQUALS, processor.getId()).build();
            ResultPage<ProcessorFilter> filterResultPage = find(new ExpressionCriteria(filterExpression));
            filters.addAll(filterResultPage.getValues());
        }

        return new ResultPage<>(filters);
    }

    private ExpressionOperator decorate(final ExpressionOperator operator) {
        final ExpressionOperator.Builder builder = new Builder()
                .op(operator.op())
                .enabled(operator.enabled());

        if (operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child instanceof ExpressionOperator) {
                    builder.addOperator(decorate((ExpressionOperator) child));

                } else if (child instanceof ExpressionTerm) {
                    ExpressionTerm term = (ExpressionTerm) child;
                    DocRef docRef = term.getDocRef();

                    try {
                        if (docRef != null) {
                            final Optional<DocRefInfo> optionalDocRefInfo = docRefInfoService.info(docRef);
                            if (optionalDocRefInfo.isPresent()) {
                                term = new ExpressionTerm.Builder()
                                        .enabled(term.enabled())
                                        .field(term.getField())
                                        .condition(term.getCondition())
                                        .value(term.getValue())
                                        .docRef(optionalDocRefInfo.get().getDocRef())
                                        .build();
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                    }

                    builder.addTerm(term);
                }
            }
        }

        return builder.build();
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final QueryData queryData,
                                             final int priority,
                                             final boolean autoPriority,
                                             final boolean enabled) {
        final long now = System.currentTimeMillis();

        return securityContext.secureResult(PERMISSION, () -> {
            final List<ReprocessDataInfo> info = new ArrayList<>();

            try {
                // We want to find all processors that need reprocessing filters.
                final List<String> processorUuidList = metaService.getProcessorUuidList(new FindMetaCriteria(queryData.getExpression()));
                processorUuidList.forEach(processorUuid -> {
                    try {
                        processorService.fetchByUuid(processorUuid).ifPresent(processor -> {
                            // Check the user has read permissions on the pipeline.
                            if (!securityContext.hasDocumentPermission(processor.getPipelineUuid(), DocumentPermissionNames.READ)) {
                                throw new PermissionException(securityContext.getUserId(),
                                        "You do not have permission to create this processor filter");
                            }

                            // If we are using auto priority then try and get a priority.
                            final int calculatedPriority = getAutoPriority(processor, priority, autoPriority);

                            // now create the filter and tracker
                            ProcessorFilter processorFilter = new ProcessorFilter();

                            // Blank tracker
                            processorFilter.setReprocess(true);
                            processorFilter.setEnabled(enabled);
                            processorFilter.setPriority(calculatedPriority);
                            processorFilter.setProcessor(processor);
                            processorFilter.setQueryData(queryData);

                            // Ensure all fields are complete.
                            processorFilter = ensureValid(processorFilter);

                            processorFilterDao.create(processorFilter, null, now);

                            info.add(new ReprocessDataInfo(Severity.INFO, "Added reprocess filter to " +
                                    getPipelineDetails(processor.getPipelineUuid()) +
                                    " with priority " +
                                    calculatedPriority,
                                    null));
                        });
                    } catch (final RuntimeException e) {
                        info.add(new ReprocessDataInfo(Severity.ERROR,
                                "Unable to add reprocess filter for processor " +
                                        processorUuid, e.getMessage()));
                    }
                });
            } catch (final RuntimeException e) {
                info.add(new ReprocessDataInfo(Severity.ERROR, e.getMessage(), null));
            }

            return info;
        });
    }

    private int getAutoPriority(final Processor processor,
                                final int defaultPriority,
                                final boolean autoPriority) {
        if (!autoPriority) {
            return defaultPriority;
        }

        int priority = defaultPriority;

        final ExpressionOperator filterExpression = new ExpressionOperator.Builder()
                .addTerm(ProcessorFilterFields.PROCESSOR_ID, ExpressionTerm.Condition.EQUALS, processor.getId())
                .addTerm(ProcessorFilterFields.PROCESSOR_FILTER_DELETED, ExpressionTerm.Condition.EQUALS, false)
                .build();
        final List<ProcessorFilter> list = processorFilterDao.find(new ExpressionCriteria(filterExpression)).getValues();
        for (final ProcessorFilter filter : list) {
            // Ignore reprocess filters.
            if (!filter.isReprocess()) {
                if (filter.isEnabled()) {
                    // If it's enabled then just return the priority.
                    return filter.getPriority();
                } else {
                    priority = filter.getPriority();
                }
            }
        }

        return priority;
    }

    private String getPipelineDetails(final String uuid) {
        try {
            final DocRef pipelineDocRef = new DocRef("Pipeline", uuid);
            final Optional<DocRefInfo> optionalDocRefInfo = docRefInfoService.info(pipelineDocRef);
            return optionalDocRefInfo
                    .map(DocRefInfo::getDocRef)
                    .map(DocRef::getName)
                    .map(name -> name + " (" + uuid + ")")
                    .orElse(uuid);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return uuid;
    }

    private ProcessorFilter ensureValid(final ProcessorFilter processorFilter) {
        if (processorFilter == null) {
            return null;
        }

        if (processorFilter.getUuid() == null) {
            processorFilter.setUuid(UUID.randomUUID().toString());
        }

        if (processorFilter.getQueryData() == null) {
            throw new IllegalArgumentException("QueryData cannot be null creating ProcessorFilter" + processorFilter);
        }

        if (processorFilter.getQueryData().getDataSource() == null) {
            processorFilter.getQueryData().setDataSource(MetaFields.STREAM_STORE_DOC_REF);
        }

        AuditUtil.stamp(securityContext.getUserId(), processorFilter);

        return processorFilter;
    }
}
