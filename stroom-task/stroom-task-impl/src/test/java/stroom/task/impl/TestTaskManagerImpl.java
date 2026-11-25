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

package stroom.task.impl;

import stroom.node.api.NodeInfo;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AppPermission;
import stroom.security.shared.HasUserRef;
import stroom.task.api.TerminationCause;
import stroom.task.shared.FindTaskCriteria;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestTaskManagerImpl {

    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private SessionIdProvider sessionIdProvider;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ExpressionPredicateFactory expressionPredicateFactory;

    private final TaskRegistry taskRegistry = new TaskRegistry();
    private TaskManagerImpl taskManager;
    private TaskContextImpl taskContext;

    @BeforeEach
    void beforeEach() {
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(securityContext).secure(Mockito.any(AppPermission.class), Mockito.any());
        Mockito.when(securityContext.secureResult(Mockito.any(AppPermission.class), Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());

        taskManager = new TaskManagerImpl(
                nodeInfo,
                sessionIdProvider,
                securityContext,
                new ExecutorProviderImpl(),
                taskRegistry,
                expressionPredicateFactory);
        taskContext = new TaskContextImpl(TaskIdFactory.create(), "test", () -> "someone", false);
        taskRegistry.put(taskContext.getTaskId(), taskContext);
    }

    @Test
    void shutdownSaysTheNodeIsStopping() {
        // Finish when terminated, as that is what the shutdown waits for.
        taskContext.setTerminateHandler(() -> taskRegistry.remove(taskContext.getTaskId()));

        taskManager.shutdown();

        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.NODE_STOPPING);
    }

    @Test
    void terminatingFromTheServerTasksScreenSaysWhoDidIt() {
        final UserRef userRef = UserRef.builder().uuid("uuid").subjectId("subject").displayName("Admin").build();
        final UserIdentity userIdentity = Mockito.mock(
                UserIdentity.class, Mockito.withSettings().extraInterfaces(HasUserRef.class));
        Mockito.when(((HasUserRef) userIdentity).getUserRef()).thenReturn(userRef);
        Mockito.when(securityContext.getUserIdentity()).thenReturn(userIdentity);
        final FindTaskCriteria criteria = new FindTaskCriteria();
        criteria.addId(taskContext.getTaskId());

        taskManager.terminate(criteria);

        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.user("Admin"));
    }

    @Test
    void terminatingAsTheProcessingUserDoesNotGiveACause() {
        // What SecurityContextImpl does for a user that has no account.
        Mockito.when(securityContext.getUserIdentity()).thenReturn(() -> "processing user");
        Mockito.when(securityContext.getUserRef()).thenThrow(new AuthenticationException("Expected"));
        final FindTaskCriteria criteria = new FindTaskCriteria();
        criteria.addId(taskContext.getTaskId());

        assertThat(taskManager.terminate(criteria).getValues()).hasSize(1);

        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.NOT_GIVEN);
    }

    @Test
    void terminatingByIdGivesTheCause() {
        taskManager.terminate(taskContext.getTaskId(), TerminationCause.HEARTBEAT_OVERDUE);

        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.HEARTBEAT_OVERDUE);
    }

    @Test
    void terminatingByIdWithoutACause() {
        taskManager.terminate(taskContext.getTaskId());

        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.NOT_GIVEN);
    }

    @Test
    void terminatingATaskThatHasGoneDoesNothing() {
        taskManager.terminate(TaskIdFactory.create(), TerminationCause.HEARTBEAT_OVERDUE);

        assertThat(taskContext.isTerminated()).isFalse();
    }
}
