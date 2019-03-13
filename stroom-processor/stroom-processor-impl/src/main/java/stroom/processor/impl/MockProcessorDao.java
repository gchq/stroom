package stroom.processor.impl;

import stroom.processor.shared.FindProcessorCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Clearable;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class MockProcessorDao implements ProcessorDao, Clearable {
    private final MockIntCrud<Processor> dao = new MockIntCrud<>();

    @Override
    public Processor create(final Processor processor) {
        final FindProcessorCriteria findProcessorCriteria = new FindProcessorCriteria();
        findProcessorCriteria.obtainPipelineUuidCriteria().setString(processor.getPipelineUuid());
        final BaseResultList<Processor> list = find(findProcessorCriteria);
        final Processor existingProcessor = list.getFirst();
        if (existingProcessor != null) {
            return existingProcessor;
        }

        return dao.create(processor);
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return dao.fetch(id);
    }

    @Override
    public Processor update(final Processor processor) {
        return dao.update(processor);
    }

    @Override
    public boolean delete(final int id) {
        return dao.delete(id);
    }

    @Override
    public BaseResultList<Processor> find(final FindProcessorCriteria criteria) {
        final List<Processor> list = dao
                .getMap()
                .values()
                .stream()
                .filter(pf -> criteria.getPipelineUuidCriteria().getString().equals(pf.getPipelineUuid()))
                .collect(Collectors.toList());

        return BaseResultList.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
