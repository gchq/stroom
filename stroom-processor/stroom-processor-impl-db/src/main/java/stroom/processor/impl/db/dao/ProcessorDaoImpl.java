package stroom.processor.impl.db.dao;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.persist.ConnectionProvider;
import stroom.processor.impl.db.tables.records.ProcessorRecord;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static stroom.processor.impl.db.tables.Processor.PROCESSOR;

public class ProcessorDaoImpl implements ProcessorDao {

    private final ConnectionProvider connectionProvider;
    private final GenericDao<ProcessorRecord, Processor, Integer> delegateDao;

    @Inject
    public ProcessorDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.delegateDao = new GenericDao<>(PROCESSOR, PROCESSOR.ID, Processor.class, connectionProvider);
    }

    @Override
    public Processor create(final Processor processor) {
        return delegateDao.create(processor);
    }

    @Override
    public Processor update(final Processor processor) {
        return delegateDao.update(processor);
    }

    @Override
    public boolean delete(final int id) {
        return delegateDao.delete(id);
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return delegateDao.fetch(id);
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
