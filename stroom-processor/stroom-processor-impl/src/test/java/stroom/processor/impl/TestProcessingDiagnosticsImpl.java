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

package stroom.processor.impl;

import stroom.node.api.NodeGroupCache;
import stroom.node.api.NodeGroupState;
import stroom.node.api.NodeService;
import stroom.node.shared.NodeGroup;
import stroom.processor.api.ProcessingDiagnostics.Limits;
import stroom.processor.api.ProcessingDiagnostics.NodeClaim;
import stroom.processor.api.ProcessingDiagnostics.Period;
import stroom.processor.api.ProcessingDiagnostics.Profile;
import stroom.processor.api.ProcessingDiagnostics.Settings;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.shared.ProcessorClaimStatus;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProfilePeriod;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.PermissionException;
import stroom.util.shared.time.Day;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class TestProcessingDiagnosticsImpl {

    private static final Instant NOW = Instant.parse("2025-06-19T02:00:00Z");

    private final ProcessorProfileCache profileCache = Mockito.mock(ProcessorProfileCache.class);
    private final NodeGroupCache nodeGroupCache = Mockito.mock(NodeGroupCache.class);
    private final NodeService nodeService = Mockito.mock(NodeService.class);
    private final ProcessorTaskResource taskResource = Mockito.mock(ProcessorTaskResource.class);
    private final ProcessorTaskDao taskDao = Mockito.mock(ProcessorTaskDao.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final TaskContextFactory taskContextFactory = Mockito.mock(TaskContextFactory.class);
    private final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);

    TestProcessingDiagnosticsImpl() {
        // Child tasks run their function as given, as the real factory does inside a task context.
        Mockito.when(taskContextFactory.childContextResult(any(), anyString(), any())).thenAnswer(invocation -> {
            final Function<TaskContext, Object> function = invocation.getArgument(2);
            return (Supplier<Object>) () -> function.apply(Mockito.mock(TaskContext.class));
        });
        Mockito.when(executorProvider.get()).thenReturn(Executors.newCachedThreadPool());
        Mockito.when(nodeService.getEnabledNodesByPriority()).thenReturn(List.of("node1", "node2"));
        Mockito.when(securityContext.hasAppPermission(any(AppPermission.class))).thenReturn(true);
    }

    @Test
    void theSettingsAreTheProcessorConfigs() {
        final Settings settings = diagnostics().getSettings();

        assertThat(settings.claimTasksOnWorker()).isFalse();
        assertThat(settings.assignTasks()).isTrue();
        assertThat(settings.tasksToCreate()).isEqualTo(new ProcessorConfig().getTasksToCreate());
        assertThat(settings.taskLeaseTimeout()).isEqualTo(new ProcessorConfig().getTaskLeaseTimeout().getDuration());
    }

    @Test
    void aProfileIsGivenWithTheThreadsEachNodeGetsNow() {
        Mockito.when(profileCache.get("Nights")).thenReturn(Optional.of(profile()));
        Mockito.when(nodeGroupCache.getSelectedGroupNodes("Night nodes")).thenReturn(Optional.of(new NodeGroupState(
                NodeGroup.builder().name("Night nodes").enabled(true).build(), Set.of("node1"))));
        Mockito.when(profileCache.getProfile("node1", "Nights", NOW)).thenReturn(new ProfileResult(4, 8));
        Mockito.when(profileCache.getProfile("node2", "Nights", NOW)).thenReturn(new ProfileResult(0, 0));

        final Profile profile = diagnostics().getProfile("Nights", NOW).orElseThrow();

        assertThat(profile.nodeGroupFound()).isTrue();
        assertThat(profile.nodeGroupEnabled()).isTrue();
        assertThat(profile.timeZone()).isEqualTo("UTC");
        assertThat(profile.periods()).containsExactly(new Period(List.of("Monday", "Wednesday"), "22:00:00",
                "06:00:00", true, 4, null));
        assertThat(profile.nodeLimits()).isEqualTo(Map.of("node1", new Limits(4, 8), "node2", new Limits(0, 0)));
    }

    @Test
    void aProfileWhoseNodeGroupIsMissingSaysSo() {
        Mockito.when(profileCache.get("Nights")).thenReturn(Optional.of(profile()));
        Mockito.when(nodeGroupCache.getSelectedGroupNodes("Night nodes")).thenReturn(Optional.empty());

        final Profile profile = diagnostics().getProfile("Nights", NOW).orElseThrow();

        assertThat(profile.nodeGroupFound()).isFalse();
        assertThat(profile.nodeLimits()).isEmpty();
        // Stroom's cache throws for a missing node group: it isn't asked.
        Mockito.verify(profileCache, Mockito.never()).getProfile(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void aProfileThatDoesntExistIsEmpty() {
        Mockito.when(profileCache.get("Nights")).thenReturn(Optional.empty());

        assertThat(diagnostics().getProfile("Nights", NOW)).isEmpty();
    }

    @Test
    void eachNodeSaysWhatItIsClaimingAndOneThatCantIsReported() {
        Mockito.when(taskResource.getClaimStatus("node1")).thenReturn(new ProcessorClaimStatus("node1", true, 3, 2,
                100L, 1, 10L, 8L, 2L, 1L, 4, 500L, null));
        Mockito.when(taskResource.getClaimStatus("node2")).thenThrow(new RuntimeException("Connection refused"));

        final List<NodeClaim> claims = diagnostics().getClaimStatus(Duration.ofSeconds(5));

        assertThat(claims).containsExactly(
                new NodeClaim("node1", true, 3, 2, 1, 4, 1L, 500L, null),
                new NodeClaim("node2", null, 0, 0, 0, 0, 0, null, "Connection refused"));
    }

    @Test
    void eachViewNeedsThePermissionItsScreenDoes() {
        Mockito.when(securityContext.hasAppPermission(any(AppPermission.class))).thenReturn(false);

        assertThatThrownBy(() -> diagnostics().getProfile("Nights", NOW)).isInstanceOf(PermissionException.class)
                .hasMessageContaining("Manage Processors");
        assertThatThrownBy(() -> diagnostics().getClaimStatus(Duration.ofSeconds(1)))
                .isInstanceOf(PermissionException.class).hasMessageContaining("View System Info");
        assertThatThrownBy(() -> diagnostics().getDeadTaskCount()).isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(taskResource, taskDao);
    }

    private ProcessingDiagnosticsImpl diagnostics() {
        return new ProcessingDiagnosticsImpl(ProcessorConfig::new, profileCache, nodeGroupCache, () -> nodeService,
                () -> taskResource, () -> taskDao, securityContext, taskContextFactory, executorProvider);
    }

    private static ProcessorProfile profile() {
        return ProcessorProfile.builder()
                .name("Nights")
                .nodeGroupName("Night nodes")
                .profilePeriods(List.of(ProfilePeriod.builder()
                        .uuid("p1")
                        .days(Days.create(Set.of(Day.WEDNESDAY, Day.MONDAY)))
                        .startTime(new Time(22, 0, 0))
                        .endTime(new Time(6, 0, 0))
                        .limitNodeThreads(true)
                        .maxNodeThreads(4)
                        .limitClusterThreads(false)
                        .build()))
                .build();
    }
}
