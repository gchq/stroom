/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.stepping;

import stroom.meta.api.MetaService;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.factory.PipelineFactory.MidPipelineScope;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.stepping.capture.ReprocessDriver;
import stroom.pipeline.stepping.capture.StreamCaptureDriver;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.capture.StreamSweepTestUtil;
import stroom.pipeline.stepping.fingerprint.ElementFingerprinter;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.SteppableElements;
import stroom.pipeline.stepping.session.SteppingSessionRegistry;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StepDataStoreManager;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class TestSteppingService {

    private static final long META_ID = 123L;

    private final TaskContextFactory taskContextFactory = Mockito.mock(TaskContextFactory.class);
    private final ReprocessDriver reprocessDriver = Mockito.mock(ReprocessDriver.class);
    private final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final TaskManager taskManager = Mockito.mock(TaskManager.class);
    private final StepDataStoreManager stepDataStoreManager = Mockito.mock(StepDataStoreManager.class);
    private final TaskContext producerTaskContext = Mockito.mock(TaskContext.class);
    private final TaskId producerTaskId = new TaskId("producer", null);

    // True only while code is running as the processing user.
    private final AtomicBoolean asProcessingUser = new AtomicBoolean(false);
    // Holds the producer task open so the synchronous wait for it times out.
    private final CountDownLatch releaseProducer = new CountDownLatch(1);

    private SteppingService steppingService;

    @BeforeEach
    void setUp() {
        Mockito.when(producerTaskContext.getTaskId()).thenReturn(producerTaskId);
        Mockito.when(stepDataStoreManager.getOrCreateStore(anyString(), anyLong()))
                .thenReturn(Mockito.mock(StepDataStore.class));

        // Mirror the real security context: asProcessingUser elevates for the duration of the runnable.
        Mockito.doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(0);
            asProcessingUser.set(true);
            try {
                runnable.run();
            } finally {
                asProcessingUser.set(false);
            }
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));

        // Mirror TaskManagerImpl.terminate(), which demands MANAGE_TASKS_PERMISSION. A stepping user does
        // not have it; the processing user does.
        Mockito.doAnswer(invocation -> {
            if (!asProcessingUser.get()) {
                throw new PermissionException(null,
                        "User 'john.smith' does not have the required permissions (MANAGE_TASKS_PERMISSION)");
            }
            return null;
        }).when(taskManager).terminate(any(TaskId.class));

        Mockito.when(taskContextFactory.context(anyString(), any()))
                .thenAnswer(invocation -> {
                    final Consumer<TaskContext> work = invocation.getArgument(1);
                    return (Runnable) () -> work.accept(producerTaskContext);
                });

        // Run the producer on its own thread, but only return once it has published its task context, so
        // the caller's timed-out wait deterministically has a task to terminate.
        final CountDownLatch contextPublished = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            final TaskContext taskContext = invocation.getArgument(0);
            final StreamSweep sweep = invocation.getArgument(6);
            StreamSweepTestUtil.setTaskContext(sweep, taskContext);
            contextPublished.countDown();
            releaseProducer.await(10, TimeUnit.SECONDS);
            return null;
        }).when(reprocessDriver).reprocess(
                any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
        final Executor executor = command -> {
            new Thread(command).start();
            try {
                assertThat(contextPublished.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };
        Mockito.when(executorProvider.get(any())).thenReturn(executor);

        steppingService = new SteppingService(
                taskContextFactory,
                () -> Mockito.mock(StreamCaptureDriver.class),
                () -> reprocessDriver,
                executorProvider,
                Mockito.mock(MetaService.class),
                securityContext,
                taskManager,
                stepDataStoreManager,
                Mockito.mock(PipelineDataHolderFactory.class),
                Mockito.mock(ElementFingerprinter.class),
                SteppingConfig::new,
                Mockito.mock(SessionStepResolver.class),
                Mockito.mock(SteppingSessionRegistry.class),
                Mockito.mock(SteppableElements.class));
    }

    @AfterEach
    void tearDown() {
        releaseProducer.countDown();
    }

    @Test
    void testTimedOutProducerIsTerminatedWithoutManageTasksPermission() {
        final PipelineStepRequest request = PipelineStepRequest.builder()
                .timeout(50L)
                .build();

        // Before the fix the producer's task was terminated as the stepping user, so the wait surfaced the
        // user's missing MANAGE_TASKS_PERMISSION instead of the timeout.
        assertThatThrownBy(() -> steppingService.reprocess(
                request,
                META_ID,
                "startElement",
                "upstreamElement",
                Mockito.mock(StepDataStore.class),
                Mockito.mock(ElementFingerprints.class),
                MidPipelineScope.ELEMENT_AND_DESCENDANTS))
                .isNotInstanceOf(PermissionException.class)
                .hasMessage("Timed out waiting for Stepping reprocess of stream " + META_ID);

        Mockito.verify(taskManager).terminate(producerTaskId);
        // The store of a failed reprocess must not be leaked.
        Mockito.verify(stepDataStoreManager).deleteSession(anyString());
    }
}
