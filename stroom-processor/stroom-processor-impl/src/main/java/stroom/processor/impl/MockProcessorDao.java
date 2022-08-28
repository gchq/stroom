package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class MockProcessorDao implements ProcessorDao, Clearable {

    private final MockIntCrud<Processor> dao = new MockIntCrud<>();

    @Override
    public Processor create(final Processor processor) {
        final ExpressionOperator findProcessorExpression = ExpressionOperator.builder()
                .addTerm(ProcessorFields.PIPELINE,
                        Condition.IS_DOC_REF,
                        new DocRef("Pipeline", processor.getPipelineUuid()))
                .build();
        final ExpressionCriteria findProcessorCriteria = new ExpressionCriteria(findProcessorExpression);
//        findProcessorCriteria.obtainPipelineUuidCriteria().setString(processor.getPipelineUuid());
        final ResultPage<Processor> list = find(findProcessorCriteria);
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
    public Optional<Processor> fetchByUuid(final String uuid) {
        return Optional.empty();
    }

    @Override
    public Optional<Processor> fetchByPipelineUuid(final String pipelineUuid) {
        return dao.getMap().values()
                .stream()
                .filter(processor -> processor.getPipelineUuid().equals(pipelineUuid))
                .findAny();
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
    public boolean logicalDelete(final int id) {
        return false;
    }

    @Override
    public ResultPage<Processor> find(final ExpressionCriteria criteria) {
        final List<Processor> list = dao
                .getMap()
                .values()
                .stream()
                .filter(pf -> {
                    final List<String> pipelineUuids = ExpressionUtil.values(criteria.getExpression(),
                            ProcessorFields.PIPELINE);
                    return pipelineUuids == null || pipelineUuids.contains(pf.getPipelineUuid());
                })
                .collect(Collectors.toList());

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
