package stroom.processor.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
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
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.db.ForceLegacyMigration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.jooq.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorTaskDaoImpl {

    private static final String NODE1 = "node1";
    private static final String NODE2 = "node2";
    private static final String FEED = "MY_FEED";

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
    }

    @Test
    void testReleaseOwned() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        final Processor processor1 = createProcessor();

        assertThat(getProcessorCount(null))
                .isEqualTo(1);

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(countTasks()).isEqualTo(3);
        assertThat(countOwned(NODE1)).isEqualTo(3);

        processorTaskDao.releaseOwnedTasks(NODE1);

        assertThat(countTasks()).isEqualTo(3);
        assertThat(countOwned(NODE1)).isEqualTo(0);
        assertThat(countOwned(null)).isEqualTo(3);

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE2, FEED);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(0);
        assertThat(countOwned(NODE2)).isEqualTo(3);
        assertThat(countOwned(null)).isEqualTo(3);

        processorTaskDao.releaseOwnedTasks(NODE1);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(0);
        assertThat(countOwned(NODE2)).isEqualTo(3);
        assertThat(countOwned(null)).isEqualTo(3);
    }

    @Test
    void testRetainOwned() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        final Processor processor1 = createProcessor();

        assertThat(getProcessorCount(null))
                .isEqualTo(1);

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE2, FEED);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1, NODE2), System.currentTimeMillis());

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1), System.currentTimeMillis() - 10000);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1), System.currentTimeMillis() + 10000);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(0);
        assertThat(countOwned(null)).isEqualTo(3);
    }

    private int countTasks() {
        final List<ProcessorTask> list = processorTaskDao.find(new ExpressionCriteria()).getValues();
        return list.size();
    }

    private int countOwned(final String nodeName) {
        int count = 0;
        List<ProcessorTask> list = processorTaskDao.find(new ExpressionCriteria()).getValues();
        for (final ProcessorTask task : list) {
            if (task.getNodeName() == null) {
                if (nodeName == null) {
                    count++;
                }
            } else if (task.getNodeName().equals(nodeName)) {
                count++;
            }
        }
        return count;
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
                                     final TaskStatus taskStatus,
                                     final String nodeName,
                                     final String feedName) {
        final long now = System.currentTimeMillis();
        final ProcessorTaskRecord processorTaskRecord = PROCESSOR_TASK.newRecord();
        processorTaskRecord.setCreateTimeMs(now);
        processorTaskRecord.setFkProcessorFilterId(processorFilter.getId());
        processorTaskRecord.setFkProcessorNodeId(processorNodeCache.getOrCreate(nodeName));
        processorTaskRecord.setFkProcessorFeedId(processorFeedCache.getOrCreate(feedName));
        processorTaskRecord.setStatus(taskStatus.getPrimitiveValue());
        processorTaskRecord.setStatusTimeMs(now);
        processorTaskRecord.setMetaId(123L);
        processorTaskRecord.setData("my data");

        JooqUtil.context(processorDbConnProvider, context -> {
            context.attach(processorTaskRecord);
            processorTaskRecord.store();
        });
    }

    private int getProcessorCount(final Condition condition) {
        return JooqUtil.getTableCountWhen(processorDbConnProvider, PROCESSOR, condition);
    }

    private int getProcessorFilterCount(final Condition condition) {
        return JooqUtil.getTableCountWhen(processorDbConnProvider, PROCESSOR_FILTER, condition);
    }
}
