/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskQueueManager;
import stroom.processor.impl.db.jooq.tables.records.ProcessorTaskRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.DocumentPermissionService;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.user.api.UserRefLookup;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.guice.AbstractTestModule;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserRef;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.jooq.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class AbstractProcessorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractProcessorTest.class);

    protected static final String NODE1 = "node1";
    protected static final String NODE2 = "node2";
    protected static final String FEED = "MY_FEED";

    @Inject
    protected ProcessorDao processorDao;
    @Inject
    protected ProcessorFilterDao processorFilterDao;
    @Inject
    protected ProcessorFilterTrackerDao processorFilterTrackerDao;
    @Inject
    protected ProcessorTaskDao processorTaskDao;
    @Inject
    protected ProcessorDbConnProvider processorDbConnProvider;
    @Inject
    protected ProcessorNodeCache processorNodeCache;
    @Inject
    protected ProcessorFeedCache processorFeedCache;
    @Inject
    protected DocumentPermissionService mockDocumentPermissionService;

    @Inject
    protected Set<Clearable> clearables;

    protected Injector injector;

    @BeforeEach
    void beforeEach() {
        injector = Guice.createInjector(
                new ProcessorDaoModule(),
                new ProcessorDbModule(),
                new MockMetricsModule(),
                new CacheModule(),
                new MockTaskModule(),
                new MockClusterLockModule(),
                new DbTestModule(),
                new MockSecurityContextModule(),
                new AbstractTestModule() {
                    @Override
                    protected void configure() {
                        final ExpressionMapper mockExpressionMapper = bindMock(ExpressionMapper.class);
                        Mockito.when(bindMock(ExpressionMapperFactory.class).create())
                                .thenReturn(mockExpressionMapper);

                        bindMock(DocRefInfoService.class);
                        bindMock(NodeInfo.class);
                        bindMock(ProcessorTaskQueueManager.class);
                        bindMock(DocumentEventLog.class);
                        bindMock(DocumentPermissionService.class);
                        bind(UserRefLookup.class).toInstance((userUuid, context) ->
                                Optional.of(UserRef.forUserUuid(userUuid)));
                    }
                });
        injector.injectMembers(this);
    }

    @AfterEach
    void clear() {
        clearables.forEach(Clearable::clear);
    }

    protected int countTasks() {
        final List<ProcessorTask> list = processorTaskDao.find(new ExpressionCriteria()).getValues();
        return list.size();
    }

    protected int countOwned(final String nodeName) {
        int count = 0;
        final List<ProcessorTask> list = processorTaskDao.find(new ExpressionCriteria()).getValues();
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

    protected Processor createProcessor() {
        final Processor processor = new Processor(new DocRef(
                PipelineDoc.TYPE,
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

    protected ProcessorFilter createProcessorFilter(final Processor processor) {
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
        processorFilter.setRunAsUser(UserRef.builder().uuid(UUID.randomUUID().toString()).build());
        stampProcessorFilter(processorFilter, "jbloggs");

        return processorFilterDao.create(processorFilter);
    }

    protected void createProcessorTask(final ProcessorFilter processorFilter,
                                       final TaskStatus taskStatus,
                                       final String nodeName,
                                       final String feedName) {
        createProcessorTask(processorFilter, taskStatus, nodeName, feedName, Instant.now());
    }

    protected void createProcessorTask(final ProcessorFilter processorFilter,
                                       final TaskStatus taskStatus,
                                       final String nodeName,
                                       final String feedName,
                                       final Instant statusTime) {
        final long now = System.currentTimeMillis();
        final ProcessorTaskRecord processorTaskRecord = PROCESSOR_TASK.newRecord();
        processorTaskRecord.setCreateTimeMs(now);
        processorTaskRecord.setFkProcessorFilterId(processorFilter.getId());
        processorTaskRecord.setFkProcessorNodeId(processorNodeCache.getOrCreate(nodeName));
        processorTaskRecord.setFkProcessorFeedId(processorFeedCache.getOrCreate(feedName));
        processorTaskRecord.setStatus(taskStatus.getPrimitiveValue());
        processorTaskRecord.setStatusTimeMs(statusTime.toEpochMilli());
        processorTaskRecord.setMetaId(123L);
        processorTaskRecord.setData("my data");

        JooqUtil.context(processorDbConnProvider, context -> {
            context.attach(processorTaskRecord);
            processorTaskRecord.store();
        });
    }

    protected int getProcessorCount(final Condition condition) {
        return JooqUtil.getTableCountWhen(processorDbConnProvider, PROCESSOR, condition);
    }

    protected int getProcessorFilterCount(final Condition condition) {
        return JooqUtil.getTableCountWhen(processorDbConnProvider, PROCESSOR_FILTER, condition);
    }

    protected int getProcessorFilterTrackerCount(final Condition condition) {
        return JooqUtil.getTableCountWhen(processorDbConnProvider, PROCESSOR_FILTER_TRACKER, condition);
    }

    protected int getProcessorTaskCount(final Condition condition) {
        return JooqUtil.getTableCountWhen(processorDbConnProvider, PROCESSOR_TASK, condition);
    }

    public Injector getInjector() {
        return injector;
    }

    public ProcessorDbConnProvider getProcessorDbConnProvider() {
        return injector.getInstance(ProcessorDbConnProvider.class);
    }

    public void dumpProcessorTable() {
        JooqUtil.context(getProcessorDbConnProvider(), context -> {
            LOGGER.debug("processor:\n{}", JooqUtil.toAsciiTable(context.select(
                            PROCESSOR.ID,
                            PROCESSOR.PIPELINE_UUID,
                            PROCESSOR.UUID,
                            PROCESSOR.DELETED,
                            PROCESSOR.ENABLED,
                            PROCESSOR.TASK_TYPE,
                            PROCESSOR.UPDATE_TIME_MS)
                    .from(PROCESSOR)
                    .orderBy(PROCESSOR.ID)
                    .fetch(), false));
        });
    }

    public void dumpProcessorFilterTable() {
        JooqUtil.context(getProcessorDbConnProvider(), context -> {
            LOGGER.debug("processor_filter:\n{}", JooqUtil.toAsciiTable(context.select(
                            PROCESSOR_FILTER.ID,
                            PROCESSOR_FILTER.FK_PROCESSOR_ID,
                            PROCESSOR_FILTER.DELETED,
                            PROCESSOR_FILTER.ENABLED,
                            PROCESSOR_FILTER.UPDATE_TIME_MS)
                    .from(PROCESSOR_FILTER)
                    .orderBy(PROCESSOR_FILTER.ID)
                    .fetch(), false));
        });
    }

    public void dumpProcessorFilterTrackerTable() {
        JooqUtil.context(getProcessorDbConnProvider(), context -> {
            LOGGER.debug("processor_filter_tracker:\n{}", JooqUtil.toAsciiTable(context.select(
                            PROCESSOR_FILTER_TRACKER.ID,
                            PROCESSOR_FILTER_TRACKER.LAST_POLL_MS,
                            PROCESSOR_FILTER_TRACKER.STATUS)
                    .from(PROCESSOR_FILTER_TRACKER)
                    .orderBy(PROCESSOR_FILTER_TRACKER.ID)
                    .fetch(), false));
        });
    }

    public void dumpProcessorTaskTable() {
        JooqUtil.context(getProcessorDbConnProvider(), context -> {
            LOGGER.debug("processor_task:\n{}", JooqUtil.toAsciiTable(context.select(
                            PROCESSOR_TASK.ID,
                            PROCESSOR_TASK.META_ID,
                            PROCESSOR_TASK.STATUS,
                            PROCESSOR_TASK.FK_PROCESSOR_NODE_ID,
                            PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID,
                            PROCESSOR_TASK.FK_PROCESSOR_FEED_ID,
                            PROCESSOR_TASK.STATUS_TIME_MS)
                    .from(PROCESSOR_TASK)
                    .orderBy(PROCESSOR_TASK.ID)
                    .fetch(), false));
        });
    }

    private void stampProcessorFilter(final ProcessorFilter processorFilter,
                                      final String auditUser) {
        AuditUtil.stamp(() -> auditUser, processorFilter);
    }
}
