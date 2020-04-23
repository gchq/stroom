package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.FindMetaCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.*;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public class MockProcessorFilterService implements ProcessorFilterService {

    private final MockProcessorFilterDao dao = new MockProcessorFilterDao();

    @Override
    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority, boolean enabled) {

        return dao.create(new ProcessorFilter());
    }

    @Override
    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
        return dao.create(new ProcessorFilter());
    }

    @Override
    public ProcessorFilter create(Processor processor, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
        return dao.create(new ProcessorFilter());
    }

    @Override
    public ProcessorFilter create(Processor processor, QueryData queryData, int priority, boolean enabled) {
        return dao.create(new ProcessorFilter());
    }

    @Override
    public ProcessorFilter create(Processor processor, DocRef processorFilterDocRef, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
        return dao.create(new ProcessorFilter());
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
