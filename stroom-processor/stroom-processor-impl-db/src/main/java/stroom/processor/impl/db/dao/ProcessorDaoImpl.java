package stroom.processor.impl.db.dao;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.persist.ConnectionProvider;
import stroom.processor.impl.db.tables.records.ProcessorRecord;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;
import stroom.util.jooq.JooqUtil;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static stroom.processor.impl.db.tables.Processor.PROCESSOR;

public class ProcessorDaoImpl implements ProcessorDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public ProcessorDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Processor create() {
        Processor processor = new Processor();

        return JooqUtil.contextResult(connectionProvider, context -> {
            final ProcessorRecord processorRecord = context.newRecord(PROCESSOR, processor);
            processorRecord.store();
            return processorRecord.into(Processor.class);
        });
    }

    @Override
    public Processor update(final Processor processor) {
        return JooqUtil.contextResultWithOptimisticLocking(connectionProvider, context -> {
            final ProcessorRecord processorRecord = context.newRecord(PROCESSOR, processor);
            processorRecord.update();
            return processorRecord.into(Processor.class);
        });
    }

    @Override
    public int delete(final int id) {
        return JooqUtil.deleteById(connectionProvider, PROCESSOR, PROCESSOR.ID, id);
    }

    @Override
    public Processor fetch(final int id) {
        return JooqUtil.fetchById(connectionProvider, PROCESSOR, PROCESSOR.ID, Processor.class, id);
    }

    @Override
    public BaseResultList<Processor> find(final FindStreamProcessorCriteria criteria) {
        final List<Processor> list = JooqUtil.contextResult(connectionProvider, context -> {
            Condition condition = DSL.trueCondition();

            if (criteria.getPipelineSet() != null) {

                if (criteria.getPipelineSet().isMatchNothing()) {
                    return BaseResultList.createUnboundedList(Collections.emptyList());
                }

                addPipelineSetToCondition(condition, criteria.getPipelineSet());
            }

            return JooqUtil.applyLimits(
                    context
                            .select()
                            .from(PROCESSOR)
                            .where(condition), criteria.getPageRequest())
                    .fetch()
                    .into(Processor.class);
        });

        return BaseResultList.createUnboundedList(list);
    }

    private void addPipelineSetToCondition(Condition condition, CriteriaSet<DocRef> pipelineSet) {
        Objects.requireNonNull(condition);
        Objects.requireNonNull(pipelineSet);

        if (Boolean.TRUE.equals(pipelineSet.getMatchNull())) {
            condition.or(
                    DSL.or(pipelineSetToInList(pipelineSet), PROCESSOR.PIPELINE_UUID.isNull()));
        } else {
            condition.or(pipelineSetToInList(pipelineSet));
        }
    }

    private Condition pipelineSetToInList(final CriteriaSet<DocRef> pipelineSet) {
        return PROCESSOR.PIPELINE_UUID.in(pipelineSet.getSet());
    }
}
