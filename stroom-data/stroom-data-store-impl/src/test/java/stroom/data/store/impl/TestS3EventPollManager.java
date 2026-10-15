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

package stroom.data.store.impl;

import stroom.receive.common.S3EventConfig;
import stroom.receive.common.S3EventService;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.time.StroomDuration;

import jakarta.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3EventPollManager {

    private final TaskContextFactory taskContextFactory = mock(TaskContextFactory.class);
    private final S3EventService s3EventService = mock(S3EventService.class, Answers.RETURNS_DEFAULTS);
    private final Provider<S3EventConfig> s3EventConfigProvider = mock(Provider.class);
    private final TaskContext parentTaskContext = mock(TaskContext.class);
    private final SimpleTaskContext childTaskContext = new SimpleTaskContext();

    @BeforeEach
    void clearInterruptedStatusBeforeTest() {
        Thread.interrupted();
    }

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    void shouldStopPollingWhenTaskIsTerminated() {
        final S3EventConfig config = mock(S3EventConfig.class);
        when(config.getRePollDuration()).thenReturn(StroomDuration.ofMinutes(5));
        givenTaskContext(config);

        doAnswer(invocation -> {
            final S3EventService.TerminationChecker terminationChecker = invocation.getArgument(0);
            assertThat(terminationChecker.isTerminated()).isFalse();
            childTaskContext.terminate();
            assertThat(terminationChecker.isTerminated()).isTrue();
            return null;
        }).when(s3EventService).poll(any());

        new S3EventPollManager(taskContextFactory, s3EventService, s3EventConfigProvider).poll();

        verify(s3EventService).poll(any());
    }

    @Test
    void shouldStopPollingWhenThreadIsInterrupted() {
        final S3EventConfig config = mock(S3EventConfig.class);
        when(config.getRePollDuration()).thenReturn(StroomDuration.ofMinutes(5));
        givenTaskContext(config);
        Thread.currentThread().interrupt();

        doAnswer(invocation -> {
            final S3EventService.TerminationChecker terminationChecker = invocation.getArgument(0);
            assertThat(terminationChecker.isTerminated()).isTrue();
            return null;
        }).when(s3EventService).poll(any());

        new S3EventPollManager(taskContextFactory, s3EventService, s3EventConfigProvider).poll();

        verify(s3EventService).poll(any());
    }

    @Test
    void shouldStopPollingWhenRePollDurationIsReached() throws Exception {
        final Method shouldStopPolling = S3EventPollManager.class.getDeclaredMethod(
                "shouldStopPolling",
                Instant.class,
                TaskContext.class);
        shouldStopPolling.setAccessible(true);

        final Boolean result = (Boolean) shouldStopPolling.invoke(
                new S3EventPollManager(taskContextFactory, s3EventService, s3EventConfigProvider),
                Instant.MIN,
                childTaskContext);

        assertThat(result).isTrue();
    }

    private void givenTaskContext(final S3EventConfig config) {
        when(s3EventConfigProvider.get()).thenReturn(config);
        when(taskContextFactory.current()).thenReturn(parentTaskContext);
        when(taskContextFactory.childContext(
                same(parentTaskContext),
                eq("SQS poll"),
                same(stroom.task.api.TerminateHandlerFactory.NOOP_FACTORY),
                any()))
                .thenAnswer(invocation -> {
                    final Consumer<TaskContext> consumer = invocation.getArgument(3);
                    return (Runnable) () -> consumer.accept(childTaskContext);
                });
    }
}
