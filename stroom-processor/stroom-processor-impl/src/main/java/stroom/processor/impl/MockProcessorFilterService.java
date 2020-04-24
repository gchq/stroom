package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.FindMetaCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.*;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Singleton
public class MockProcessorFilterService implements ProcessorFilterService {

    private final MockProcessorFilterDao dao = new MockProcessorFilterDao();

    private Random idRandom = new Random (0);

    private final ProcessorService processorService;

    @Inject
    MockProcessorFilterService(final ProcessorService processorService){
        this.processorService = processorService;
    }

    @Override
    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority, boolean enabled) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setPipelineUuid(pipelineRef.getUuid());
        filter.setQueryData(queryData);
        filter.setPriority(priority);
        filter.setEnabled(enabled);
        Processor processor = processorService.create(pipelineRef, enabled);

        filter.setProcessor(processor);
        return dao.create(filter);
    }

    @Override
    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
        return create (pipelineRef, queryData, priority, enabled, null);
    }

    @Override
    public ProcessorFilter create(Processor processor, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setProcessor(processor);
        filter.setQueryData(queryData);
        filter.setPriority(priority);
        filter.setEnabled(enabled);
        return dao.create(filter);
    }

    @Override
    public ProcessorFilter create(Processor processor, QueryData queryData, int priority, boolean enabled) {
        return create (processor, queryData, priority, enabled, null);
    }

    @Override
    public ProcessorFilter create(Processor processor, DocRef processorFilterDocRef, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setProcessor(processor);
        filter.setQueryData(queryData);
        filter.setPriority(priority);
        filter.setUuid(processorFilterDocRef.getUuid());
        filter.setEnabled(enabled);
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
    public List<ReprocessDataInfo> reprocess(FindMetaCriteria criteria) {
        return null;
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
