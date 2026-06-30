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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.data.ShardMergeEventData;
import stroom.planb.impl.data.SharedFileStoreMergeProcessor;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.ShardWriters;
import stroom.planb.impl.db.ShardWriters.ShardWriter;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.time.SimpleDuration;

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
import java.util.ArrayList;
import java.util.Collections;
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

class TestPathwaysIntegration {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    @TempDir
    Path tempDir;

    private StatePaths statePaths;
    private PlanBConfig planBConfig;
    private TracesDoc tracesDoc;
    private PathwaysDoc pathwaysDoc;
    private Path sharedRootDir;
    private Executor executor;

    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private ShardManager shardManager;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private TaskContextFactory taskContextFactory;
    @Mock
    private PlanBDocCache planBDocCache;
    @Mock
    private stroom.planb.impl.data.FileTransferClient fileTransferClient;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private EntityEventBus entityEventBus;
    @Mock
    private PathwaysStore pathwaysStore;
    @Mock
    private TracesDocStore tracesDocStore;
    @Mock
    private MessageReceiverFactory messageReceiverFactory;
    @Mock
    private PathCreator pathCreator;

    private AutoCloseable mocks;
    private PathwaysEntityEventHandler eventHandler;
    private PathwaysProcessor pathwaysProcessor;

    private final List<EntityEvent> firedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        statePaths = new StatePaths(tempDir.resolve("local_state"));
        sharedRootDir = tempDir.resolve("shared_store");
        Files.createDirectories(sharedRootDir);

        planBConfig = PlanBConfig.builder()
                .nodeList(Collections.singletonList("test-node"))
                .build();

        // Create a TracesDoc
        tracesDoc = TracesDoc.tracesBuilder()
                .uuid(UUID.randomUUID().toString())
                .name("test_traces")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                2,
                                sharedRootDir.toAbsolutePath().toString(),
                                null))
                        .maxStoreSize(ByteSize.ofMebibytes(10).getBytes())
                        .retention(new RetentionSettings.Builder().duration(SimpleDuration.ZERO).enabled(false).build())
                        .build())
                .build();

        // Create a PathwaysDoc referencing our TracesDoc
        pathwaysDoc = PathwaysDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test_pathway")
                .tracesDocRef(new DocRef(TracesDoc.TYPE, tracesDoc.getUuid(), tracesDoc.getName()))
                .build();

        executor = Runnable::run;
        when(executorProvider.get()).thenReturn(executor);

        // Setup TaskContextFactory stubbing
        doAnswer(invocation -> {
            final Consumer<TaskContext> consumer = invocation.getArgument(1);
            return (Runnable) () -> consumer.accept(Mockito.mock(TaskContext.class));
        }).when(taskContextFactory).context(any(String.class), any(Consumer.class));

        doAnswer(invocation -> {
            final Consumer<TaskContext> consumer = invocation.getArgument(2);
            return (Runnable) () -> consumer.accept(Mockito.mock(TaskContext.class));
        }).when(taskContextFactory).childContext(any(TaskContext.class), any(String.class), any(Consumer.class));

        // Setup SecurityContext stubbing
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));

        when(nodeInfo.getThisNodeName()).thenReturn("test-node");

        // Set up the pathCreator mock to return our tempDir when requested
        when(pathCreator.toAppPath(any(String.class))).thenReturn(tempDir.resolve("pathways_local"));

        // Instantiate the PathwaysProcessor
        pathwaysProcessor = new PathwaysProcessor(
                pathwaysStore,
                messageReceiverFactory,
                pathCreator,
                BYTE_BUFFERS,
                new PathwaySerde(BYTE_BUFFER_FACTORY),
                shardManager,
                nodeInfo,
                tracesDocStore,
                clusterLockService,
                BYTE_BUFFER_FACTORY
        );

        // Instantiate the handler
        eventHandler = new PathwaysEntityEventHandler(
                () -> pathwaysStore,
                () -> pathwaysProcessor,
                taskContextFactory,
                securityContext,
                executorProvider
        );

        // Mock EntityEventBus to record fired events and dispatch them to the handler
        doAnswer(invocation -> {
            final EntityEvent event = invocation.getArgument(0);
            firedEvents.add(event);
            eventHandler.onChange(event);
            return null;
        }).when(entityEventBus).fire(any(EntityEvent.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        FileUtil.deleteDir(tempDir);
    }

    @Test
    void testEndToEndPathwaysProcessingTrigger() throws Exception {
        // 1. Setup mock behaviors
        @SuppressWarnings("rawtypes")
        final stroom.docstore.api.DocumentActionHandler tracesHandlerMock = Mockito.mock(
                stroom.docstore.api.DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ImportExportActionHandler.class));
        final ImportExportActionHandler tracesIeHandler = (ImportExportActionHandler) tracesHandlerMock;

        @SuppressWarnings("rawtypes")
        final stroom.docstore.api.DocumentActionHandler pathwaysHandlerMock = Mockito.mock(
                stroom.docstore.api.DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ImportExportActionHandler.class));
        final ImportExportActionHandler pathwaysIeHandler = (ImportExportActionHandler) pathwaysHandlerMock;

        final Map<DocumentTypeName, DocumentActionHandler> documentActionHandlers = Map.of(
                new DocumentTypeName(TracesDoc.TYPE), tracesHandlerMock,
                new DocumentTypeName(PathwaysDoc.TYPE), pathwaysHandlerMock
        );

        when(tracesIeHandler.listDocuments()).thenReturn(Set.of(tracesDoc.asDocRef()));
        when(tracesHandlerMock.readDocument(tracesDoc.asDocRef())).thenReturn(tracesDoc);
        when(tracesIeHandler.getType()).thenReturn(TracesDoc.TYPE);

        when(pathwaysIeHandler.listDocuments()).thenReturn(Set.of(pathwaysDoc.asDocRef()));
        when(pathwaysHandlerMock.readDocument(pathwaysDoc.asDocRef())).thenReturn(pathwaysDoc);
        when(pathwaysIeHandler.getType()).thenReturn(PathwaysDoc.TYPE);

        when(pathwaysStore.list()).thenReturn(List.of(pathwaysDoc.asDocRef()));
        when(pathwaysStore.readDocument(pathwaysDoc.asDocRef())).thenReturn(pathwaysDoc);

        final TracesDoc mappedTracesDoc = TracesDoc.tracesBuilder()
                .uuid(tracesDoc.getUuid())
                .name(tracesDoc.getName())
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                tracesDoc.getShardCount(),
                                tracesDoc.getSharedPath(),
                                null))
                        .build())
                .build();
        when(tracesDocStore.readDocument(any(DocRef.class))).thenReturn(mappedTracesDoc);

        // Setup lock behavior to run task synchronously
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).tryLock(any(String.class), any(Runnable.class));

        // Stub MessageReceiverFactory to do nothing
        doAnswer(invocation -> {
            final Consumer<MessageReceiver> consumer = invocation.getArgument(1);
            consumer.accept((severity, message) -> {});
            return null;
        }).when(messageReceiverFactory).create(any(String.class), any(Consumer.class));

        // Create staging batches
        final ShardWriters shardWriters = new ShardWriters(
                planBDocCache,
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                statePaths,
                fileTransferClient,
                () -> planBConfig,
                () -> documentActionHandlers,
                securityContext
        );

        when(planBDocCache.get(tracesDoc.getName())).thenReturn(tracesDoc);
        when(planBDocCache.getAll()).thenReturn(List.of(tracesDoc));
        final stroom.meta.shared.Meta meta = Mockito.mock(stroom.meta.shared.Meta.class);
        when(meta.getId()).thenReturn(123L);

        // Instantiate real ShardManager and SharedFileStoreMergeProcessor

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
                () -> documentActionHandlers,
                realShardManager,
                nodeInfo,
                securityContext,
                () -> entityEventBus,
                taskContextFactory,
                planBDocCache);

        // Write some traces using ShardWriter
        try (final ShardWriter shardWriter = shardWriters.createWriter(meta)) {
            final SpanKey spanKey = new SpanKey("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null);
            final SpanValue spanValue = SpanValue.builder()
                    .name("my-test-span")
                    .build();
            final SpanKV spanKV = new SpanKV(spanKey, spanValue);
            shardWriter.addSpanValue(tracesDoc, spanKV);
        }

        final Path sharedProcessingDir = sharedRootDir.resolve("processing").resolve(tracesDoc.getUuid());
        assertThat(sharedProcessingDir).exists();

        // Find the batch directory name that was created
        final Path shard0Dir = sharedProcessingDir.resolve("0000");
        assertThat(shard0Dir).exists();
        final Path batchDir = Files.list(shard0Dir).findFirst().orElseThrow();
        final String batchDirName = batchDir.getFileName().toString();

        // Run the merge!
        mergeProcessor.merge();

        // 2. Verify that the UPDATE event was fired
        assertThat(firedEvents).hasSize(1);
        final EntityEvent event = firedEvents.get(0);
        assertThat(event.getAction()).isEqualTo(stroom.util.entityevent.EntityAction.UPDATE);
        assertThat(event.getDocRef()).isEqualTo(tracesDoc.asDocRef());
        assertThat(event.hasDataClass(ShardMergeEventData.class)).isTrue();

        final ShardMergeEventData data = event.getDataObject(ShardMergeEventData.class);
        assertThat(data.getShardIndex()).isEqualTo(0);
        assertThat(data.getBatchDirName()).isEqualTo(batchDirName);

        // 3. Verify that the batch directory was successfully processed and deleted/cleaned up
        assertThat(batchDir).doesNotExist();

        // Verify that pathways DB was created and pushed to the shared store
        final Path sharedPathwaysDir = sharedRootDir.resolve("pathways").resolve(pathwaysDoc.getUuid());
        assertThat(sharedPathwaysDir.resolve("data.mdb")).exists();
        assertThat(sharedPathwaysDir.resolve(".version")).exists();
        assertThat(sharedPathwaysDir.resolve(".complete")).exists();
    }
}
