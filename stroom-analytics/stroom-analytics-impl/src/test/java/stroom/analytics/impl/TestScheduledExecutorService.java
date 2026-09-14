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

package stroom.analytics.impl;

import stroom.analytics.impl.ScheduledExecutorService.ExecutionResult;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestScheduledExecutorService {

    private final ExecutionScheduleDao dao = mock(ExecutionScheduleDao.class);
    private final ExecutorProvider executorProvider = mock(ExecutorProvider.class);
    private final TaskContextFactory taskContextFactory = mock(TaskContextFactory.class);
    private final TaskContext taskContext = mock(TaskContext.class);
    private final SecurityContext securityContext = mock(SecurityContext.class);
    private final NodeInfo nodeInfo = mock(NodeInfo.class);
    private final DocFinder docFinder = mock(DocFinder.class);
    @SuppressWarnings("unchecked")
    private final ScheduledExecutable<DocRef> executable = mock(ScheduledExecutable.class, CALLS_REAL_METHODS);
    private final Map<String, ExecutionSchedule> schedules = new LinkedHashMap<>();
    private final List<String> executions = new ArrayList<>();
    private final List<UserRef> users = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    private final List<ExecutionHistory> history = new ArrayList<>();
    private final UserRef caller = UserRef.forUserUuid("caller");
    private final AtomicReference<UserRef> currentUser = new AtomicReference<>(caller);
    private ScheduledExecutorService<DocRef> service;

    @BeforeEach
    void setUp() {
        when(executorProvider.get()).thenReturn(command -> {
            throw new RejectedExecutionException("No spare pool threads");
        });
        when(nodeInfo.getThisNodeName()).thenReturn("node1");
        when(taskContextFactory.current()).thenReturn(taskContext);
        when(taskContextFactory.<Boolean>childContextResult(any(), anyString(), any()))
                .thenAnswer(invocation -> (Supplier<Boolean>) () -> invocation
                        .<Function<TaskContext, Boolean>>getArgument(2)
                        .apply(taskContext));
        when(taskContextFactory.<Boolean>contextResult(anyString(), any()))
                .thenAnswer(invocation -> (Supplier<Boolean>) () -> invocation
                        .<Function<TaskContext, Boolean>>getArgument(1)
                        .apply(taskContext));
        when(securityContext.asProcessingUserResult(any())).thenAnswer(invocation -> invocation
                .<Supplier<?>>getArgument(0)
                .get());
        doAnswer(invocation -> {
            final UserRef previous = currentUser.getAndSet(invocation.getArgument(0));
            try {
                invocation.<Runnable>getArgument(1).run();
            } finally {
                currentUser.set(previous);
            }
            return null;
        }).when(securityContext).asUser(any(UserRef.class), any(Runnable.class));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).useAsRead(any());
        when(dao.fetchScheduleByUuid(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(schedules.get(invocation.<String>getArgument(0))));
        when(dao.fetchExecutionSchedule(any())).thenAnswer(invocation -> {
            final ExecutionScheduleRequest request = invocation.getArgument(0);
            return ResultPage.createUnboundedList(schedules.values().stream()
                    .filter(schedule -> schedule.getOwningDoc().equals(request.getOwnerDocRef()))
                    .toList());
        });
        when(dao.updateExecutionSchedule(any())).thenAnswer(invocation -> {
            final ExecutionSchedule schedule = invocation.getArgument(0);
            schedules.put(schedule.getUuid(), schedule);
            return schedule;
        });
        doAnswer(invocation -> {
            history.add(invocation.getArgument(0));
            return null;
        }).when(dao).addExecutionHistory(any());
        when(executable.getProcessType()).thenReturn("test");
        when(executable.getDocRef(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executable.getIdentity(any())).thenAnswer(invocation -> invocation
                .<DocRef>getArgument(0)
                .getName());
        when(executable.load(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executable.reload(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(executable.run(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            final ExecutionSchedule schedule = invocation.getArgument(4);
            executions.add(schedule.getUuid());
            users.add(currentUser.get());
            threads.add(Thread.currentThread());
            return new ExecutionResult("Complete", null);
        });
        service = Guice.createInjector(binder -> {
            binder.bind(ExecutionScheduleDao.class).toInstance(dao);
            binder.bind(ExecutorProvider.class).toInstance(executorProvider);
            binder.bind(TaskContextFactory.class).toInstance(taskContextFactory);
            binder.bind(SecurityContext.class).toInstance(securityContext);
            binder.bind(NodeInfo.class).toInstance(nodeInfo);
            binder.bind(DocFinder.class).toInstance(docFinder);
        }).getInstance(Key.get(new TypeLiteral<ScheduledExecutorService<DocRef>>() {}));
    }

    @Test
    void executesDocumentsAndSchedulesWithoutSparePoolThreads() {
        final DocRef first = new DocRef("Test", "first", "First");
        final DocRef second = new DocRef("Test", "second", "Second");
        final ExecutionSchedule firstSchedule = addSchedule("one", first);
        final ExecutionSchedule secondSchedule = addSchedule("two", first);
        final ExecutionSchedule thirdSchedule = addSchedule("three", second);
        when(executable.getDocs()).thenReturn(List.of(first, second));

        service.exec(executable);

        assertThat(executions).containsExactly("one", "two", "three");
        assertThat(users).containsExactly(firstSchedule.getRunAsUser(), secondSchedule.getRunAsUser(),
                thirdSchedule.getRunAsUser());
        assertThat(threads).containsOnly(Thread.currentThread());
        assertThat(currentUser.get()).isEqualTo(caller);
        assertThat(history).hasSize(3);
        verify(executable).postExecuteTidyUp(List.of(first, second));
    }

    @Test
    void executesNowWithoutSparePoolThreads() {
        final ExecutionSchedule schedule = addSchedule("now", new DocRef("Test", "one", "One"));

        service.executeNow(schedule, executable);

        assertThat(executions).containsExactly("now");
        assertThat(threads).containsOnly(Thread.currentThread());
        assertThat(users).containsExactly(schedule.getRunAsUser());
        assertThat(currentUser.get()).isEqualTo(caller);
        assertThat(history).hasSize(1);
    }

    @Test
    void continuesAfterFailureLoadingOneDocument() {
        final DocRef first = new DocRef("Test", "first", "First");
        final DocRef second = new DocRef("Test", "second", "Second");
        addSchedule("failed", first);
        addSchedule("working", second);
        when(executable.getDocs()).thenReturn(List.of(first, second));
        when(executable.reload(first)).thenThrow(new IllegalStateException("Document unavailable"));

        service.exec(executable);

        assertThat(executions).containsExactly("working");
        assertThat(currentUser.get()).isEqualTo(caller);
        verify(executable).postExecuteTidyUp(List.of(first, second));
    }

    @Test
    void doesNotExecuteTerminatedParent() {
        final DocRef doc = new DocRef("Test", "one", "One");
        addSchedule("one", doc);
        when(executable.getDocs()).thenReturn(List.of(doc));
        when(taskContext.isTerminated()).thenReturn(true);

        service.exec(executable);

        assertThat(executions).isEmpty();
        assertThat(history).isEmpty();
    }

    @Test
    void doesNotExecuteInterruptedCaller() {
        final DocRef doc = new DocRef("Test", "one", "One");
        addSchedule("one", doc);
        when(executable.getDocs()).thenReturn(List.of(doc));
        try {
            Thread.currentThread().interrupt();
            service.exec(executable);
            assertThat(executions).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private ExecutionSchedule addSchedule(final String id, final DocRef doc) {
        final ExecutionSchedule schedule = ExecutionSchedule.builder()
                .uuid(id)
                .name(id)
                .enabled(true)
                .nodeName("node1")
                .schedule(Schedule.builder().type(ScheduleType.INSTANT).build())
                .owningDoc(doc)
                .runAsUser(UserRef.forUserUuid(id + "-user"))
                .build();
        schedules.put(id, schedule);
        return schedule;
    }
}
