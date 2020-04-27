package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class MockProcessorFilterDao implements ProcessorFilterDao, Clearable {
    private final MockIntCrud<ProcessorFilter> dao = new MockIntCrud<>();

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        if (processorFilter.getProcessorFilterTracker() == null) {
            processorFilter.setProcessorFilterTracker(new ProcessorFilterTracker());
        }
        return dao.create(processorFilter);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter, Long trackerStartMs) {
        return create (processorFilter, null);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return dao.fetch(id);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        return dao.update(processorFilter);
    }

    @Override
    public boolean delete(final int id) {
        return dao.delete(id);
    }

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        final List<ProcessorFilter> list = dao
                .getMap()
                .values()
                .stream()
//                .filter(pf -> criteria.getPipelineUuidCriteria().getString().equals(pf.getPipelineUuid()))
                .collect(Collectors.toList());

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
