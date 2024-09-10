package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class MockProcessorFilterService implements ProcessorFilterService {

    private final ProcessorService processorService;
    private final MockProcessorFilterDao dao;
    private final SecurityContext securityContext;

    @Inject
    MockProcessorFilterService(final ProcessorService processorService,
                               final MockProcessorFilterDao dao,
                               final SecurityContext securityContext) {
        this.processorService = processorService;
        this.dao = dao;
        this.securityContext = securityContext;
    }

    @Override
    public ProcessorFilter create(final CreateProcessFilterRequest request) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setPipelineUuid(request.getPipeline().getUuid());
        filter.setQueryData(request.getQueryData());
        filter.setPriority(request.getPriority());
        filter.setMaxProcessingTasks(request.getMaxProcessingTasks());
        filter.setEnabled(request.isEnabled());
        filter.setMinMetaCreateTimeMs(request.getMinMetaCreateTimeMs());
        filter.setMaxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        setRunAs(request, filter);
        Processor processor = processorService.create(
                request.getProcessorType(),
                request.getPipeline(),
                request.isEnabled());

        filter.setProcessor(processor);
        return dao.create(filter);
    }

    private void setRunAs(final CreateProcessFilterRequest request, final ProcessorFilter filter) {
        filter.setRunAsUser(NullSafe.getOrElse(request,
                CreateProcessFilterRequest::getRunAsUser,
                securityContext.getUserRef()));
    }

    //    @Override
//    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority,
//    boolean enabled, Long trackerStartMs) {
//        return create (pipelineRef, queryData, priority, enabled, null);
//    }
//
    @Override
    public ProcessorFilter create(final Processor processor,
                                  final CreateProcessFilterRequest request) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setProcessor(processor);
        filter.setQueryData(request.getQueryData());
        filter.setPriority(request.getPriority());
        filter.setMaxProcessingTasks(request.getMaxProcessingTasks());
        filter.setEnabled(request.isEnabled());
        filter.setMinMetaCreateTimeMs(request.getMinMetaCreateTimeMs());
        filter.setMaxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        setRunAs(request, filter);
        return dao.create(filter);
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final CreateProcessFilterRequest request) {
        return null;
    }

    @Override
    public ProcessorFilter importFilter(final Processor processor,
                                        final DocRef processorFilterDocRef,
                                        final CreateProcessFilterRequest request) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setProcessor(processor);
        filter.setQueryData(request.getQueryData());
        filter.setPriority(request.getPriority());
        filter.setMaxProcessingTasks(request.getMaxProcessingTasks());
        filter.setUuid(processorFilterDocRef.getUuid());
        filter.setEnabled(request.isEnabled());
        filter.setMinMetaCreateTimeMs(request.getMinMetaCreateTimeMs());
        filter.setMaxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        setRunAs(request, filter);
        return dao.create(filter);
    }

    @Override
    public ResultPage<ProcessorFilter> find(ExpressionCriteria criteria) {
        return dao.find(criteria);
    }

    @Override
    public ResultPage<ProcessorListRow> find(FetchProcessorRequest request) {
        return null;
    }

    @Override
    public ProcessorFilterRow getRow(final ProcessorFilter processorFilter) {
        return null;
    }

    @Override
    public ResultPage<ProcessorFilter> find(DocRef pipelineDocRef) {
        return null;
    }

    @Override
    public void setPriority(Integer id, Integer priority) {
    }

    @Override
    public void setMaxProcessingTasks(Integer id, Integer maxProcessingTasks) {
    }

    @Override
    public void setEnabled(Integer id, Boolean enabled) {

    }

    @Override
    public ProcessorFilter create(ProcessorFilter processorFilter) {
        return dao.create(processorFilter);
    }

    @Override
    public Optional<ProcessorFilter> fetch(int id) {
        return dao.fetch(id);
    }

    @Override
    public ProcessorFilter update(ProcessorFilter processorFilter) {
        return dao.update(processorFilter);
    }

    @Override
    public boolean delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<String> getPipelineName(final ProcessorType processorType, final String uuid) {
        return Optional.empty();
    }

    @Override
    public Optional<ProcessorFilter> fetchByUuid(final String uuid) {
        return dao.fetchByUuid(uuid);
    }
}
