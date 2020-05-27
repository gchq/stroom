package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

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
    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority, boolean autoPriority, boolean enabled) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setPipelineUuid(pipelineRef.getUuid());
        filter.setQueryData(queryData);
        filter.setPriority(priority);
        filter.setEnabled(enabled);
        Processor processor = processorService.create(pipelineRef, enabled);

        filter.setProcessor(processor);
        return dao.create(filter);
    }

    //    @Override
//    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority, boolean enabled, Long trackerStartMs) {
//        return create (pipelineRef, queryData, priority, enabled, null);
//    }
//
    @Override
    public ProcessorFilter create(Processor processor, QueryData queryData, int priority, boolean autoPriority, boolean enabled) {
        ProcessorFilter filter = new ProcessorFilter();
        filter.setProcessor(processor);
        filter.setQueryData(queryData);
        filter.setPriority(priority);
        filter.setEnabled(enabled);
        return dao.create(filter);
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final QueryData criteria, final int priority, final boolean autoPriority, final boolean enabled) {
        return null;
    }

    //    @Override
//    public ProcessorFilter create(Processor processor, QueryData queryData, int priority, boolean enabled) {
//        return create (processor, queryData, priority, enabled);
//    }

    @Override
    public ProcessorFilter importFilter(final Processor processor,
                                        final DocRef processorFilterDocRef,
                                        final QueryData queryData,
                                        final int priority,
                                        boolean autoPriority,
                                        final boolean enabled,
                                        final boolean reprocess,
                                        final Long trackerStartMs) {
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
