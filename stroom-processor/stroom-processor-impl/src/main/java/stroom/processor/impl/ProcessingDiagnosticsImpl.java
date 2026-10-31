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
import stroom.processor.api.ProcessingDiagnostics;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.shared.ProcessorClaimStatus;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProfilePeriod;
import stroom.query.language.functions.UserTimeZoneUtil;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.time.Day;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Reads processing state for [ProcessingDiagnostics], as the user, changing nothing.
@Singleton
public class ProcessingDiagnosticsImpl implements ProcessingDiagnostics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessingDiagnosticsImpl.class);

    private final Provider<ProcessorConfig> processorConfigProvider;
    private final ProcessorProfileCache processorProfileCache;
    private final NodeGroupCache nodeGroupCache;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<ProcessorTaskResource> processorTaskResourceProvider;
    private final Provider<ProcessorTaskDao> processorTaskDaoProvider;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    public ProcessingDiagnosticsImpl(final Provider<ProcessorConfig> processorConfigProvider,
                                     final ProcessorProfileCache processorProfileCache,
                                     final NodeGroupCache nodeGroupCache,
                                     final Provider<NodeService> nodeServiceProvider,
                                     final Provider<ProcessorTaskResource> processorTaskResourceProvider,
                                     final Provider<ProcessorTaskDao> processorTaskDaoProvider,
                                     final SecurityContext securityContext,
                                     final TaskContextFactory taskContextFactory,
                                     final ExecutorProvider executorProvider) {
        this.processorConfigProvider = processorConfigProvider;
        this.processorProfileCache = processorProfileCache;
        this.nodeGroupCache = nodeGroupCache;
        this.nodeServiceProvider = nodeServiceProvider;
        this.processorTaskResourceProvider = processorTaskResourceProvider;
        this.processorTaskDaoProvider = processorTaskDaoProvider;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get();
    }

    @Override
    public Settings getSettings() {
        final ProcessorConfig config = processorConfigProvider.get();
        return new Settings(
                config.isClaimTasksOnWorker(),
                config.isAssignTasks(),
                config.isFillTaskQueue(),
                config.isCreateTasksBeyondProcessLimit(),
                config.isUseMaxMetaIdFromPreviousPoll(),
                config.getTasksToCreate(),
                config.getQueueSize(),
                NullSafe.get(config.getTaskLeaseTimeout(), StroomDuration::getDuration),
                NullSafe.get(config.getSkipNonProducingFiltersMaxDuration(), StroomDuration::getDuration));
    }

    @Override
    public Optional<Profile> getProfile(final String name, final Instant now) {
        requirePermission(AppPermission.MANAGE_PROCESSORS_PERMISSION, "processor profiles");
        final Optional<ProcessorProfile> found = processorProfileCache.get(name);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final ProcessorProfile profile = found.get();
        final Optional<NodeGroupState> nodeGroup = nodeGroupCache.getSelectedGroupNodes(profile.getNodeGroupName());
        final Map<String, Limits> nodeLimits = new LinkedHashMap<>();
        if (nodeGroup.isPresent()) {
            for (final String node : nodeServiceProvider.get().getEnabledNodesByPriority()) {
                // Worked out as task assignment works it out, so what is reported is what Stroom does.
                final ProfileResult result = processorProfileCache.getProfile(node, name, now);
                nodeLimits.put(node, new Limits(result.maxNodeThreads(), result.maxClusterThreads()));
            }
        }
        final List<Period> periods = NullSafe.list(profile.getProfilePeriods()).stream()
                .map(ProcessingDiagnosticsImpl::period)
                .toList();
        return Optional.of(new Profile(
                profile.getName(),
                profile.getNodeGroupName(),
                nodeGroup.isPresent(),
                nodeGroup.map(NodeGroupState::isEnabled).orElse(false),
                zone(profile),
                periods,
                nodeLimits));
    }

    @Override
    public List<NodeClaim> getClaimStatus(final Duration timeout) {
        requirePermission(AppPermission.VIEW_SYSTEM_INFO_PERMISSION, "what each node is claiming");
        final List<String> nodes = nodeServiceProvider.get().getEnabledNodesByPriority();
        final TaskContext parent = taskContextFactory.current();
        final Map<String, CompletableFuture<ProcessorClaimStatus>> asked = new LinkedHashMap<>();
        for (final String node : nodes) {
            // Each node asked in its own child task, so the call is made as this user and the nodes are asked at
            // once: one slow node costs the timeout, not the whole answer.
            asked.put(node, CompletableFuture.supplyAsync(taskContextFactory.childContextResult(parent,
                    "Asking node " + node + " what it is claiming",
                    taskContext -> processorTaskResourceProvider.get().getClaimStatus(node)), executor));
        }
        final long deadline = System.nanoTime() + timeout.toNanos();
        final List<NodeClaim> claims = new ArrayList<>();
        for (final Map.Entry<String, CompletableFuture<ProcessorClaimStatus>> entry : asked.entrySet()) {
            claims.add(answer(entry.getKey(), entry.getValue(), deadline, timeout));
        }
        return claims;
    }

    @Override
    public int getDeadTaskCount() {
        requirePermission(AppPermission.MANAGE_PROCESSORS_PERMISSION, "processor tasks");
        final Instant cutOff = Instant.now().minus(processorConfigProvider.get().getTaskLeaseTimeout().getDuration());
        return processorTaskDaoProvider.get().countDeadTasks(cutOff);
    }

    // A node's answer, or why there wasn't one by the deadline.
    private static NodeClaim answer(final String node,
                                    final CompletableFuture<ProcessorClaimStatus> future,
                                    final long deadline,
                                    final Duration timeout) {
        try {
            final long remaining = Math.max(0, deadline - System.nanoTime());
            final ProcessorClaimStatus status = future.get(remaining, TimeUnit.NANOSECONDS);
            if (status == null) {
                return unreachable(node, "no answer");
            }
            if (status.getError() != null) {
                return unreachable(node, status.getError());
            }
            return new NodeClaim(node,
                    status.isClaimTasksOnWorker(),
                    status.getEligibleFilterCount(),
                    status.getFiltersWithWorkCount(),
                    status.getBackedOffFilterCount(),
                    status.getTasksInFlight(),
                    status.getLockedMetaReleases(),
                    status.getLastHeartbeatAgeMs(),
                    null);
        } catch (final TimeoutException e) {
            future.cancel(true);
            return unreachable(node, "no answer within " + timeout.toMillis() + " ms");
        } catch (final ExecutionException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to get claim status from node {}", node), e);
            return unreachable(node, NullSafe.nonBlankStringElse(NullSafe.get(e.getCause(), Throwable::getMessage),
                    String.valueOf(e.getCause())));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return unreachable(node, "interrupted");
        }
    }

    private static NodeClaim unreachable(final String node, final String error) {
        return new NodeClaim(node, null, 0, 0, 0, 0, 0, null, error);
    }

    // The zone the periods are in, as the profile cache works it out ("UTC" rather than "Z").
    private static String zone(final ProcessorProfile profile) {
        final ZoneId zone = UserTimeZoneUtil.getZoneId(profile.getTimeZone(), null);
        return ZoneOffset.UTC.equals(zone)
                ? "UTC"
                : zone.getId();
    }

    private static Period period(final ProfilePeriod period) {
        final List<String> days = NullSafe.set(NullSafe.get(period.getDays(), Days::getDays)).stream()
                .sorted(Comparator.comparingInt(Day::getValue))
                .map(Day::getDisplayValue)
                .toList();
        final Time start = period.getStartTime();
        final Time end = period.getEndTime();
        return new Period(days,
                time(start),
                time(end),
                start != null && end != null && seconds(end) <= seconds(start),
                period.isLimitNodeThreads()
                        ? period.getMaxNodeThreads()
                        : null,
                period.isLimitClusterThreads()
                        ? period.getMaxClusterThreads()
                        : null);
    }

    private static String time(final Time time) {
        return time == null
                ? null
                : String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
    }

    private static int seconds(final Time time) {
        return time.getHour() * 3600 + time.getMinute() * 60 + time.getSecond();
    }

    private void requirePermission(final AppPermission permission, final String what) {
        if (!securityContext.hasAppPermission(permission)) {
            throw new PermissionException(securityContext.getUserRef(), "You need the "
                                                                        + permission.getDisplayValue()
                                                                        + " permission to see " + what);
        }
    }
}
