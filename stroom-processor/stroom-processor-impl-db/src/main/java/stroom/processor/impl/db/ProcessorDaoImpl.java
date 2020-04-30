package stroom.processor.impl.db;

import org.jooq.Condition;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorDataSource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;

class ProcessorDaoImpl implements ProcessorDao {
    private final ProcessorDbConnProvider processorDbConnProvider;
    private final GenericDao<ProcessorRecord, Processor, Integer> genericDao;
    private final ExpressionMapper expressionMapper;

    @Inject
    public ProcessorDaoImpl(final ProcessorDbConnProvider processorDbConnProvider,
                            final ExpressionMapperFactory expressionMapperFactory) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.genericDao = new GenericDao<>(PROCESSOR, PROCESSOR.ID, Processor.class, processorDbConnProvider);

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorDataSource.CREATE_USER, PROCESSOR_FILTER.CREATE_USER, value -> value);
        expressionMapper.map(ProcessorDataSource.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value);
        expressionMapper.map(ProcessorDataSource.UUID, PROCESSOR.UUID, value -> value);
    }

    @Override
    public Processor create(final Processor processor) {
        // We don't use the delegate DAO here as we want to handle potential duplicates carefully so this behaves as a getOrCreate method.
        return JooqUtil.contextResult(processorDbConnProvider, context -> {
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
        return genericDao.update(processor);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public ResultPage<Processor> find(final ExpressionCriteria criteria) {
        final Collection<Condition> conditions = expressionMapper.apply(criteria.getExpression());

        final List<Processor> list = JooqUtil.contextResult(processorDbConnProvider, context -> context
                .select()
                .from(PROCESSOR)
                .where(conditions)
                .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                .fetch()
                .into(Processor.class));

        return ResultPage.createUnboundedList(list);
    }
}
