package stroom.processor.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.NodeInfo;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.processor.impl.db.jooq.tables.records.ProcessorTaskRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.db.ForceLegacyMigration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.jooq.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import javax.inject.Inject;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorDaoImpl {

    @Inject
    private ProcessorDao processorDao;
    @Inject
    private ProcessorFilterDao processorFilterDao;
    @Inject
    private ProcessorTaskDao processorTaskDao;
    @Inject
    private ProcessorDbConnProvider processorDbConnProvider;
    @Inject
    private ProcessorNodeCache processorNodeCache;
    @Inject
    private ProcessorFeedCache processorFeedCache;

    private int feedId;
    private int nodeId;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(
                new ProcessorDaoModule(),
                new ProcessorDbModule(),
                new CacheModule(),
                new MockTaskModule(),
                new MockClusterLockModule(),
                new DbTestModule(),
                new MockSecurityContextModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        final ExpressionMapper expressionMapper = Mockito.mock(ExpressionMapper.class);
                        final ExpressionMapperFactory expressionMapperFactory =
                                Mockito.mock(ExpressionMapperFactory.class);
                        Mockito.when(expressionMapperFactory.create())
                                .thenReturn(expressionMapper);

                        bind(ExpressionMapper.class).toInstance(expressionMapper);
                        bind(ExpressionMapperFactory.class)
                                .toInstance(expressionMapperFactory);
                        bind(DocRefInfoService.class)
                                .toInstance(Mockito.mock(DocRefInfoService.class));
                        bind(NodeInfo.class)
                                .toInstance(Mockito.mock(NodeInfo.class));
                        bind(ProcessorTaskManager.class)
                                .toInstance(Mockito.mock(ProcessorTaskManager.class));
                        bind(DocumentEventLog.class)
                                .toInstance(Mockito.mock(DocumentEventLog.class));
                        // Not using all the DB modules so just bind to an empty anonymous class
                        bind(ForceLegacyMigration.class).toInstance(new ForceLegacyMigration() {
                        });
                    }
                });
        injector.injectMembers(this);

        nodeId = processorNodeCache.getOrCreate("node1");
        feedId = processorFeedCache.getOrCreate("MY_FEED");
    }

    @Test
    void logicalDelete() {
        Assertions.assertThat(getProcessorCount(null))
                .isEqualTo(0);

        final Processor processor1 = createProcessor();

        Assertions.assertThat(getProcessorCount(null))
                .isEqualTo(1);

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED);

        Assertions.assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);
        Assertions.assertThat(getProcessorTaskCount(null))
                .isEqualTo(2);

        final Processor processor2 = createProcessor();

        Assertions.assertThat(getProcessorCount(null))
                .isEqualTo(2);

        final ProcessorFilter processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.UNPROCESSED);
        createProcessorTask(processorFilter2, TaskStatus.ASSIGNED);

        Assertions.assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        Assertions.assertThat(getProcessorTaskCount(null))
                .isEqualTo(4);

        processorDao.logicalDelete(processor1.getId());

        // No change to row counts as they have been logically deleted
        Assertions.assertThat(getProcessorCount(null))
                .isEqualTo(2);
        Assertions.assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        Assertions.assertThat(getProcessorTaskCount(null))
                .isEqualTo(4);

        // Now make sure the right number have been set to a deleted state
        Assertions.assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(1);
        Assertions.assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        Assertions.assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(2);
    }

    private Processor createProcessor() {
        final Processor processor = new Processor(new DocRef(
                PipelineDoc.DOCUMENT_TYPE,
                UUID.randomUUID().toString()));
        processor.setCreateTimeMs(System.currentTimeMillis());
        processor.setCreateUser("jbloggs");
        processor.setUpdateTimeMs(System.currentTimeMillis());
        processor.setUpdateUser("jbloggs");
        processor.setUuid(UUID.randomUUID().toString());
        processor.setEnabled(true);
        processor.setDeleted(false);
        return processorDao.create(processor);
    }

    private ProcessorFilter createProcessorFilter(final Processor processor) {
        final ProcessorFilter processorFilter = new ProcessorFilter();
        processorFilter.setProcessor(processor);
        processorFilter.setCreateTimeMs(System.currentTimeMillis());
        processorFilter.setCreateUser("jbloggs");
        processorFilter.setUpdateTimeMs(System.currentTimeMillis());
        processorFilter.setUpdateUser("jbloggs");
        processorFilter.setDeleted(false);
        processorFilter.setQueryData(QueryData.builder()
                .build());
        processorFilter.setUuid(UUID.randomUUID().toString());

        return processorFilterDao.create(processorFilter);
    }

    private void createProcessorTask(final ProcessorFilter processorFilter,
                                     final TaskStatus taskStatus) {

        final ProcessorTaskRecord processorTaskRecord = PROCESSOR_TASK.newRecord();
        processorTaskRecord.setFkProcessorFilterId(processorFilter.getId());
        processorTaskRecord.setFkProcessorNodeId(nodeId);
        processorTaskRecord.setFkProcessorFeedId(feedId);
        processorTaskRecord.setStatus(taskStatus.getPrimitiveValue());
        processorTaskRecord.setMetaId(123L);
        processorTaskRecord.setData("my data");

        JooqUtil.context(processorDbConnProvider, context -> {
            context.attach(processorTaskRecord);
            processorTaskRecord.store();
        });
    }

    private int getProcessorCount(final Condition condition) {
        return JooqUtil.getTableCount(processorDbConnProvider, PROCESSOR, condition);
    }

    private int getProcessorFilterCount(final Condition condition) {
        return JooqUtil.getTableCount(processorDbConnProvider, PROCESSOR_FILTER, condition);
    }

    private int getProcessorTaskCount(final Condition condition) {
        return JooqUtil.getTableCount(processorDbConnProvider, PROCESSOR_TASK, condition);
    }
}
