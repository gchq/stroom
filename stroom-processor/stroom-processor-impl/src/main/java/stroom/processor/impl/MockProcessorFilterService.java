package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockProcessorFilterService implements ProcessorFilterService {

    private final MockProcessorFilterDao dao = new MockProcessorFilterDao();

//    private Random idRandom = new Random (0);

    private final ProcessorService processorService;

    @Inject
    MockProcessorFilterService(final ProcessorService processorService) {
        this.processorService = processorService;
    }

    @Override
    public ProcessorFilter create(final CreateProcessFilterRequest request) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setPipelineUuid(request.getPipeline().getUuid());
        filter.setQueryData(request.getQueryData());
        filter.setPriority(request.getPriority());
        filter.setEnabled(request.isEnabled());
        filter.setMinMetaCreateTimeMs(request.getMinMetaCreateTimeMs());
        filter.setMaxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        Processor processor = processorService.create(request.getPipeline(), request.isEnabled());

        filter.setProcessor(processor);
        return dao.create(filter);
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
        filter.setEnabled(request.isEnabled());
        filter.setMinMetaCreateTimeMs(request.getMinMetaCreateTimeMs());
        filter.setMaxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
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
        filter.setUuid(processorFilterDocRef.getUuid());
        filter.setEnabled(request.isEnabled());
        filter.setMinMetaCreateTimeMs(request.getMinMetaCreateTimeMs());
        filter.setMaxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
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
    public ResultPage<ProcessorFilter> find(DocRef pipelineDocRef) {
        return null;
    }

    @Override
    public void setPriority(Integer id, Integer priority) {
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
}
