/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.BatchDestination;
import stroom.planb.impl.db.DefaultBatchDestination;
import stroom.planb.impl.db.PlanBStreamWriter;
import stroom.planb.impl.db.PlanBStreamWriterFactory;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.impl.db.state.StateDb;
import stroom.planb.impl.db.state.StateRequest;
import stroom.planb.impl.fs.SharedFileStoreMergeProcessor;
import stroom.planb.impl.fs.SharedFileStorePartDestination;
import stroom.planb.impl.rest.FileTransferClient;
import stroom.planb.impl.rest.RestPartDestination;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.query.language.functions.ValString;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class TestSharedFileStoreMerge {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSharedFileStoreMerge.class);

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    @TempDir
    Path tempDir;

    private StatePaths statePaths;
    private PlanBConfig planBConfig;
    private PlanBDoc doc;
    private Path sharedRootDir;
    private Executor executor;

    @Mock
    private ClusterLockService clusterLockService;
    private Map<DocumentTypeName, DocumentActionHandler> documentActionHandlers;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private TaskContextFactory taskContextFactory;
    @Mock
    private PlanBDocCache planBDocCache;

    @Mock
    private FileTransferClient fileTransferClient;
    @Mock
    private ExecutorProvider executorProvider;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        documentActionHandlers = new HashMap<>();
        statePaths = new StatePaths(tempDir.resolve("local_state"));
        sharedRootDir = tempDir.resolve("shared_store");
        Files.createDirectories(sharedRootDir);

        planBConfig = PlanBConfig.builder()
                .nodeList(Collections.singletonList("test-node"))
                .build();

        doc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test_map")
                .stateType(StateType.STATE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(2, sharedRootDir.toAbsolutePath().toString()))
                        .build())
                .build();

        executor = Runnable::run;
        when(executorProvider.get()).thenReturn(executor);

        // Setup TaskContextFactory stubbing to just run the runnable
        doAnswer(invocation -> {
            final Consumer<TaskContext> consumer = invocation.getArgument(1);
            return (Runnable) () -> consumer.accept(Mockito.mock(TaskContext.class));
        }).when(taskContextFactory).context(any(String.class), any(Consumer.class));

        doAnswer(invocation -> {
            final Consumer<TaskContext> consumer = invocation.getArgument(2);
            return (Runnable) () -> consumer.accept(Mockito.mock(TaskContext.class));
        }).when(taskContextFactory).childContext(any(TaskContext.class), any(String.class), any(Consumer.class));

        // Setup SecurityContext stubbing to run the runnable
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));

        when(nodeInfo.getThisNodeName()).thenReturn("test-node");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        FileUtil.deleteDir(tempDir);
    }

    @Test
    void testEndToEndSharedFileStoreMerge() throws Exception {
        // 1. Setup mock behaviors
        @SuppressWarnings("rawtypes")
        final stroom.docstore.api.DocumentActionHandler handlerMock = Mockito.mock(
                stroom.docstore.api.DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ImportExportActionHandler.class));
        final ImportExportActionHandler ieHandler = (ImportExportActionHandler) handlerMock;

        documentActionHandlers.put(new DocumentTypeName(PlanBDoc.TYPE), handlerMock);
        when(planBDocCache.get(doc.getName())).thenReturn(doc);
        when(planBDocCache.getAll()).thenReturn(List.of(doc));
        when(ieHandler.listDocuments()).thenReturn(Set.of(doc.asDocRef()));
        when(handlerMock.readDocument(doc.asDocRef())).thenReturn(doc);
        when(ieHandler.getType()).thenReturn(PlanBDoc.TYPE);

        // Setup lock behavior to run task synchronously
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).tryLock(any(String.class), any(Runnable.class));

        // Instantiate PlanBStreamWriterFactory and write sharded staging batches to shared store
        final BatchDestination batchPublisher = new DefaultBatchDestination();
        final PlanBStreamWriterFactory shardWriters = new PlanBStreamWriterFactory(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                statePaths,
                batchPublisher,
                new SharedFileStorePartDestination(),
                new RestPartDestination(fileTransferClient));

        final Meta meta = Mockito.mock(Meta.class);
        when(meta.getId()).thenReturn(123L);

        try (final PlanBStreamWriter shardWriter = shardWriters.createWriter(meta)) {
            shardWriter.addState(doc, new State(KeyPrefix.create("key1"), ValString.create("value1")));
            shardWriter.addState(doc, new State(KeyPrefix.create("key2"), ValString.create("value2")));
        }

        // Verify that staging directories exist on shared storage
        final Path sharedProcessingDir = sharedRootDir.resolve("processing").resolve(doc.getUuid());
        assertThat(sharedProcessingDir).exists();


        // 2. Instantiate real ShardManager and SharedFileStoreMergeProcessor
        final ShardManager realShardManager = new ShardManager(
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                planBDocCache,
                null,
                nodeInfo,
                () -> planBConfig,
                statePaths,
                fileTransferClient,
                taskContextFactory,
                executorProvider,
                () -> documentActionHandlers
        );

        final SharedFileStoreMergeProcessor mergeProcessor = new SharedFileStoreMergeProcessor(
                clusterLockService,
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                () -> planBConfig,
                statePaths,
                nodeInfo,
                securityContext,
                taskContextFactory,
                planBDocCache
        );

        // Run the merge
        mergeProcessor.merge();

        // Give async executors a brief moment to complete the merge and exceed the 1-second sync check interval
        Thread.sleep(1500);

        // 3. Verify files are copied back and cleaned up on shared store
        // The processing folder should now be empty or deleted (each batch folder containing .complete should be gone)
        try (var batchDirs = Files.walk(sharedProcessingDir, 3)) {
            final boolean hasComplete = batchDirs
                    .filter(p -> p.getFileName().toString().equals(".complete"))
                    .anyMatch(Files::exists);
            assertThat(hasComplete).isFalse();
        }

        // The shards folder should now contain the main merged shards
        final Path sharedShardDir = sharedRootDir.resolve("shards").resolve(doc.getUuid());
        assertThat(sharedShardDir).exists();

        // Both shard 0 and shard 1 should exist, have a .version and .complete file
        for (int i = 0; i < 2; i++) {
            final Path shardIndexDir = sharedShardDir.resolve(String.format("%04d", i));
            assertThat(shardIndexDir.resolve("data.mdb")).exists();
            assertThat(shardIndexDir.resolve(".version")).exists();
            assertThat(shardIndexDir.resolve(".complete")).exists();
        }

        // Verify query capability on the merged database
        final String val1 = realShardManager.get(doc.getName(), "key1", reader -> {
            if (reader instanceof final StateDb stateDb) {
                return stateDb.getState(new StateRequest(KeyPrefix.create("key1"))).val().toString();
            }
            return null;
        });
        final String val2 = realShardManager.get(doc.getName(), "key2", reader -> {
            if (reader instanceof final StateDb stateDb) {
                return stateDb.getState(new StateRequest(KeyPrefix.create("key2"))).val().toString();
            }
            return null;
        });

        assertThat(val1).isEqualTo("value1");
        assertThat(val2).isEqualTo("value2");
    }
}
