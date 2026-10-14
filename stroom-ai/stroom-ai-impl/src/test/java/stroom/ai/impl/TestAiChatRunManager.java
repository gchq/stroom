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

import stroom.ai.api.AiService;
import stroom.ai.impl.AiChatRunManager.AiChatBusyException;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
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
    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final UserService userService = Mockito.mock(UserService.class);
    private AiConfig aiConfig = new AiConfig();
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
        Mockito.when(securityContext.asProcessingUserResult(any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        runManager = new AiChatRunManager(runDao, new SimpleTaskContextFactory(), executorProvider, nodeInfo,
                securityContext, aiService, aiDao, userService, () -> aiConfig);
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
    void testExecute_stoppedWhileWorkingEndsStoppedWithTheReason() {
        // The run was asked to stop (by the user) while it worked, so it is no longer RUNNING.
        Mockito.when(runDao.compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.DONE), any(), any()))
                .thenReturn(false);
        Mockito.when(runDao.get(5)).thenReturn(Optional.of(new AiChatRun(
                5, 1, AiChatRunStatus.STOPPING, AiChatRunStopReason.USER, 0L, null, false, null)));

        runManager.execute(run, r -> {
        }, () -> false);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPED),
                eq(AiChatRunStopReason.USER), isNull());
    }

    @Test
    void testStop_setsStoppingAndTellsTheLocalRun() {
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.RUNNING, "node1", null))
                .thenReturn(Optional.of(run));
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(run));
        Mockito.when(runDao.compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPING),
                eq(AiChatRunStopReason.USER), isNull())).thenReturn(true);
        final AtomicBoolean stopSeen = new AtomicBoolean();

        runManager.start(1, r -> {
            // Stop is asked for while the work is running.
            assertThat(runManager.stop(1, AiChatRunStopReason.USER)).isTrue();
            stopSeen.set(runManager.isStopRequested(1));
        });

        assertThat(stopSeen).isTrue();
        // Once the run has finished it is no longer this node's.
        assertThat(runManager.isStopRequested(1)).isFalse();
    }

    @Test
    void testExecute_waitingForTheUserGivesUpTheRun() {
        Mockito.when(runDao.compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.WAITING_FOR_USER),
                any(), any())).thenReturn(true);

        runManager.execute(run, r -> {
            throw new AiChatRunManager.WaitingForUserException("needs approval");
        }, () -> false);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), eq(List.of(AiChatRunStatus.RUNNING)),
                eq(AiChatRunStatus.WAITING_FOR_USER), eq(AiChatRunStopReason.NEEDS_APPROVAL), isNull());
        Mockito.verify(runDao, Mockito.never()).compareAndSetStatus(eq(5), anyCollection(),
                eq(AiChatRunStatus.DONE), any(), any());
        Mockito.verify(runDao, Mockito.never()).compareAndSetStatus(eq(5), anyCollection(),
                eq(AiChatRunStatus.FAILED), any(), any());
    }

    @Test
    void testContinueRun() {
        final AiChatRun waiting = new AiChatRun(5, 1, AiChatRunStatus.WAITING_FOR_USER,
                AiChatRunStopReason.NEEDS_APPROVAL, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(waiting));
        Mockito.when(runDao.compareAndSetStatus(5, List.of(AiChatRunStatus.WAITING_FOR_USER),
                AiChatRunStatus.RUNNING, null, null)).thenReturn(true);
        final AtomicBoolean worked = new AtomicBoolean();

        runManager.continueRun(1, r -> worked.set(true));

        assertThat(worked).isTrue();
        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.DONE),
                isNull(), isNull());
    }

    @Test
    void testContinueRun_nothingWaiting() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> runManager.continueRun(1, r -> {
        })).isInstanceOf(AiChatRunManager.AiChatBusyException.class);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runManager.continueRun(1, r -> {
        })).isInstanceOf(AiChatRunManager.AiChatBusyException.class);
    }

    @Test
    void testStop_noActiveRun() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());

        assertThat(runManager.stop(1, AiChatRunStopReason.USER)).isFalse();
    }

    @Test
    void testTick() {
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.RUNNING, "node1", null))
                .thenReturn(Optional.of(run));
        Mockito.when(runDao.getStatuses(java.util.List.of(5)))
                .thenReturn(java.util.Map.of(5, AiChatRunStatus.STOPPING));
        final AiChatRun stale = new AiChatRun(9, 2, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.interruptStale(Mockito.anyLong())).thenReturn(java.util.List.of(stale));
        final AtomicBoolean stopSeen = new AtomicBoolean();

        runManager.start(1, r -> {
            runManager.tick();
            stopSeen.set(runManager.isStopRequested(1));
        });

        Mockito.verify(runDao).heartbeat(java.util.List.of(5));
        // A stop asked for on another node is passed on.
        assertThat(stopSeen).isTrue();
        // A run whose node stopped sending heartbeats loses its progress line.
        Mockito.verify(aiService).deleteWorkingMessages(2);
    }

    @Test
    void testExecute_taskTerminatedButWorkReturnedNormally() {
        // E.g. the session ended, its task was terminated, and the question code returned early.
        Mockito.when(runDao.get(5)).thenReturn(Optional.of(run));

        runManager.execute(run, r -> {
        }, () -> true);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPED),
                eq(AiChatRunStopReason.TERMINATED), isNull());
        Mockito.verify(runDao, Mockito.never()).compareAndSetStatus(eq(5), anyCollection(),
                eq(AiChatRunStatus.DONE), any(), any());
    }

    @Test
    void testStop_runNotBeingWorkedOnStopsAtOnce() {
        final AiChatRun waiting = new AiChatRun(6, 1, AiChatRunStatus.WAITING_FOR_USER, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(waiting));

        runManager.stop(1, AiChatRunStopReason.USER);

        Mockito.verify(runDao).compareAndSetStatus(eq(6), anyCollection(), eq(AiChatRunStatus.STOPPED),
                eq(AiChatRunStopReason.USER), isNull());
    }

    @Test
    void testStopForSession() {
        final String hash = AiChatRunManager.sessionIdHash("session-1");
        Mockito.when(runDao.getActiveBySession(hash)).thenReturn(java.util.List.of(run));

        runManager.stopForSession("session-1", AiChatRunStopReason.SIGN_OUT);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPING),
                eq(AiChatRunStopReason.SIGN_OUT), isNull());
        // Runs whose tasks were terminated before this was told get the right reason.
        Mockito.verify(runDao).correctStopReason(eq(hash), eq(AiChatRunStopReason.TERMINATED),
                eq(AiChatRunStopReason.SIGN_OUT), Mockito.anyLong());
    }

    @Test
    void testSessionIdHash() {
        assertThat(AiChatRunManager.sessionIdHash(null)).isNull();
        assertThat(AiChatRunManager.sessionIdHash("abc"))
                .hasSize(64)
                .isEqualTo(AiChatRunManager.sessionIdHash("abc"))
                .isNotEqualTo(AiChatRunManager.sessionIdHash("abd"))
                .doesNotContain("abc");
    }

    @Test
    void testTick_stopsRunsPastTheTimeLimit() {
        aiConfig = new AiConfig(null, null, StroomDuration.ZERO, null, null, null, null, null, null, null, null, null);
        startAndTick(true);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPING),
                eq(AiChatRunStopReason.RUN_LIMIT), isNull());
    }

    @Test
    void testTick_stopsRunsOfDisabledUsers() {
        final User user = Mockito.mock(User.class);
        Mockito.when(user.isEnabled()).thenReturn(false);
        Mockito.when(userService.loadByUuid("user1")).thenReturn(Optional.of(user));
        startAndTick(true);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPING),
                eq(AiChatRunStopReason.ACCOUNT_DISABLED), isNull());
    }

    @Test
    void testTick_marksRunsUnattendedWhenTheUserHasGone() {
        enabledUser();
        // The browser last checked in well over the unattended time ago.
        Mockito.when(aiDao.getLastPollMs("user1")).thenReturn(System.currentTimeMillis() - 3_600_000L);
        final AtomicBoolean unattended = new AtomicBoolean();

        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.RUNNING, "node1", null))
                .thenReturn(Optional.of(run));
        Mockito.when(runDao.get(5)).thenReturn(Optional.of(run));
        runManager.start(1, r -> {
            runManager.tick();
            unattended.set(runManager.isUnattended(1));
        });

        assertThat(unattended).isTrue();
        Mockito.verify(runDao).setUnattended(5, true);
    }

    @Test
    void testTick_attendedRun() {
        enabledUser();
        Mockito.when(aiDao.getLastPollMs("user1")).thenReturn(System.currentTimeMillis());
        final AtomicBoolean unattended = new AtomicBoolean(true);

        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.RUNNING, "node1", null))
                .thenReturn(Optional.of(run));
        Mockito.when(runDao.get(5)).thenReturn(Optional.of(run));
        runManager.start(1, r -> {
            runManager.tick();
            unattended.set(runManager.isUnattended(1));
        });

        assertThat(unattended).isFalse();
        Mockito.verify(runDao, Mockito.never()).setUnattended(eq(5), Mockito.anyBoolean());
        Mockito.verify(runDao, Mockito.never()).compareAndSetStatus(eq(5), anyCollection(),
                eq(AiChatRunStatus.STOPPING), any(), any());
    }

    private void enabledUser() {
        final User user = Mockito.mock(User.class);
        Mockito.when(user.isEnabled()).thenReturn(true);
        Mockito.when(userService.loadByUuid("user1")).thenReturn(Optional.of(user));
    }

    private void startAndTick(final boolean running) {
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.RUNNING, "node1", null))
                .thenReturn(Optional.of(run));
        Mockito.when(runDao.get(5)).thenReturn(Optional.of(running
                ? run
                : new AiChatRun(5, 1, AiChatRunStatus.DONE, null, 0L, 1L, false, null)));
        runManager.start(1, r -> runManager.tick());
    }

    @Test
    void testStart_queuedWhenTheUserIsAtTheLimit() {
        aiConfig = new AiConfig(null, null, null, null, 1, 20, null, null, null, null, null, null);
        Mockito.when(runDao.countWorking("user1")).thenReturn(1);
        final AiChatRun queued = new AiChatRun(7, 1, AiChatRunStatus.QUEUED, null, 0L, null, false, null);
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.QUEUED, "node1", null))
                .thenReturn(Optional.of(queued));
        final AtomicBoolean worked = new AtomicBoolean();

        final AiChatRun started = runManager.start(1, r -> worked.set(true));

        assertThat(started.getStatus()).isEqualTo(AiChatRunStatus.QUEUED);
        assertThat(worked).isFalse();

        // The user's other run finishes, so the queued one can start.
        Mockito.when(runDao.countWorking("user1")).thenReturn(0);
        Mockito.when(runDao.compareAndSetStatus(eq(7), anyCollection(), eq(AiChatRunStatus.RUNNING), isNull(),
                isNull())).thenReturn(true);
        runManager.startQueued();

        assertThat(worked).isTrue();
    }

    @Test
    void testStartQueued_runStoppedWhileQueuedIsDropped() {
        aiConfig = new AiConfig(null, null, null, null, 1, 20, null, null, null, null, null, null);
        Mockito.when(runDao.countWorking("user1")).thenReturn(1);
        final AiChatRun queued = new AiChatRun(7, 1, AiChatRunStatus.QUEUED, null, 0L, null, false, null);
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.QUEUED, "node1", null))
                .thenReturn(Optional.of(queued));
        final AtomicBoolean worked = new AtomicBoolean();
        runManager.start(1, r -> worked.set(true));

        Mockito.when(runDao.countWorking("user1")).thenReturn(0);
        // Not QUEUED any more, as it was stopped.
        Mockito.when(runDao.compareAndSetStatus(eq(7), anyCollection(), eq(AiChatRunStatus.RUNNING), isNull(),
                isNull())).thenReturn(false);
        runManager.startQueued();
        runManager.startQueued();

        assertThat(worked).isFalse();
        Mockito.verify(runDao, Mockito.times(1)).compareAndSetStatus(eq(7), anyCollection(),
                eq(AiChatRunStatus.RUNNING), isNull(), isNull());
    }

    @Test
    void testStart_queuedWhenTheNodeIsAtTheLimit() {
        aiConfig = new AiConfig(null, null, null, null, 3, 0, null, null, null, null, null, null);
        final AiChatRun queued = new AiChatRun(7, 1, AiChatRunStatus.QUEUED, null, 0L, null, false, null);
        Mockito.when(runDao.createIfNoneActive(1, "user1", AiChatRunStatus.QUEUED, "node1", null))
                .thenReturn(Optional.of(queued));

        assertThat(runManager.start(1, r -> {
        }).getStatus()).isEqualTo(AiChatRunStatus.QUEUED);
    }

    @Test
    void testIsResumable() {
        assertThat(AiChatRunManager.isResumable(null)).isFalse();
        for (final AiChatRunStatus status : AiChatRunStatus.values()) {
            final boolean expected = status == AiChatRunStatus.STOPPED
                                     || status == AiChatRunStatus.INTERRUPTED
                                     || status == AiChatRunStatus.FAILED;
            assertThat(AiChatRunManager.isResumable(new AiChatRun(1, 1, status, null, 0L, null, false, null)))
                    .as(status.name())
                    .isEqualTo(expected);
        }
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
        }, () -> false);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.FAILED),
                isNull(), eq("model unavailable"));
    }

    @Test
    void testExecute_terminated() {
        runManager.execute(run, r -> {
            throw new TaskTerminatedException();
        }, () -> false);

        Mockito.verify(runDao).compareAndSetStatus(eq(5), anyCollection(), eq(AiChatRunStatus.STOPPED),
                eq(AiChatRunStopReason.TERMINATED), isNull());
    }
}
