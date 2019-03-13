package stroom.processor.impl.db;

import org.jooq.Condition;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorRecord;
import stroom.processor.shared.FindProcessorCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;

class ProcessorDaoImpl implements ProcessorDao {
    private final ConnectionProvider connectionProvider;
    private final GenericDao<ProcessorRecord, Processor, Integer> delegateDao;

    @Inject
    public ProcessorDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.delegateDao = new GenericDao<>(PROCESSOR, PROCESSOR.ID, Processor.class, connectionProvider);
    }

    @Override
    public Processor create(final Processor processor) {
        // We don't use the delegate DAO here as we want to handle potential duplicates carefully so this behaves as a getOrCreate method.
        return JooqUtil.contextResult(connectionProvider, context -> {
            final Optional<ProcessorRecord> optional = context
                    .insertInto(PROCESSOR,
                            PROCESSOR.CREATE_TIME_MS,
                            PROCESSOR.CREATE_USER,
                            PROCESSOR.UPDATE_TIME_MS,
                            PROCESSOR.UPDATE_USER,
                            PROCESSOR.UUID,
                            PROCESSOR.TASK_TYPE,
                            PROCESSOR.PIPELINE_UUID,
                            PROCESSOR.ENABLED)
                    .values(processor.getCreateTimeMs(),
                            processor.getCreateUser(),
                            processor.getUpdateTimeMs(),
                            processor.getUpdateUser(),
                            processor.getUuid(),
                            processor.getTaskType(),
                            processor.getPipelineUuid(),
                            processor.isEnabled())
                    .onDuplicateKeyIgnore()
                    .returning(PROCESSOR.ID)
                    .fetchOptional();

            if (optional.isPresent()) {
                final Integer id = optional.get().getId();
                return context
                        .select()
                        .from(PROCESSOR)
                        .where(PROCESSOR.ID.eq(id))
                        .fetchOneInto(Processor.class);
            }

            return context
                    .select()
                    .from(PROCESSOR)
                    .where(PROCESSOR.PIPELINE_UUID.eq(processor.getPipelineUuid()))
                    .fetchOneInto(Processor.class);
        });
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
    public BaseResultList<Processor> find(final FindProcessorCriteria criteria) {
        final List<Condition> conditions = new ArrayList<>();
        JooqUtil.getStringCondition(PROCESSOR.PIPELINE_UUID, criteria.getPipelineUuidCriteria()).ifPresent(conditions::add);

        final List<Processor> list = JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(PROCESSOR)
                .where(conditions)
                .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                .fetch()
                .into(Processor.class));

        return BaseResultList.createUnboundedList(list);
    }
}
