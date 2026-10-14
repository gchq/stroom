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

package stroom.ai.impl;

import stroom.ai.impl.AiChatRunManager.AiChatBusyException;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class TestAiChatRunManager {

    private final AiChatRunDao runDao = Mockito.mock(AiChatRunDao.class);
    private final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiChatRun run = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
    private AiChatRunManager runManager;

    @BeforeEach
    void setUp() {
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("user1").build());
        // Runs the background work straight away, on this thread.
        final ExecutorProvider executorProvider = new ExecutorProvider() {
            @Override
            public Executor get() {
                return Runnable::run;
            }

            @Override
            public Executor get(final stroom.task.shared.ThreadPool threadPool) {
                return Runnable::run;
            }
        };
        runManager = new AiChatRunManager(runDao, new SimpleTaskContextFactory(), executorProvider, nodeInfo,
                securityContext);
    }

    @Test
    void testStart_runsTheWorkAndMarksTheRunDone() {
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.RUNNING, "node1", null))
                .thenReturn(Optional.of(run));
        final AtomicBoolean worked = new AtomicBoolean();

        final AiChatRun started = runManager.start(1, r -> worked.set(true));

        assertThat(started).isSameAs(run);
        assertThat(worked).isTrue();
        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.DONE),
                isNull(), isNull());
    }

    @Test
    void testStart_busyChat() {
        Mockito.when(runDao.createIfNoneActive(any(Integer.class), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> runManager.start(1, r -> {
        })).isInstanceOf(AiChatBusyException.class);
    }

    @Test
    void testExecute_failure() {
        runManager.execute(run, r -> {
            throw new RuntimeException("model unavailable");
        });

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.FAILED),
                isNull(), eq("model unavailable"));
    }

    @Test
    void testExecute_terminated() {
        runManager.execute(run, r -> {
            throw new TaskTerminatedException();
        });

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPED),
                eq(AiChatRunStopReason.TERMINATED), isNull());
    }
}
