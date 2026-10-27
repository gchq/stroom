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

package stroom.ai.impl.tools;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.job.api.JobStates;
import stroom.job.api.JobStates.JobState;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.node.api.NodeService;
import stroom.processor.api.ProcessingDiagnostics;
import stroom.processor.api.ProcessingDiagnostics.NodeClaim;
import stroom.processor.api.ProcessingDiagnostics.Profile;
import stroom.processor.api.ProcessingDiagnostics.Settings;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// [ProcessingFacts] read from Stroom, as the user. Each read checks the permission the matching screen needs
/// (Manage Processors, Manage Jobs, Manage Nodes, View System Info) and throws [PermissionException] without it.
class StroomProcessingFacts implements ProcessingFacts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomProcessingFacts.class);

    /// How long the nodes are given to answer.
    static final Duration NODE_TIMEOUT = Duration.ofSeconds(5);
    /// The most processor filters read before picking the highest priority ones.
    private static final int MAX_FILTERS_READ = 5_000;

    private final Provider<ProcessingDiagnostics> processingDiagnosticsProvider;
    private final Provider<JobStates> jobStatesProvider;
    private final Provider<ClusterNodeManager> clusterNodeManagerProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;
    private final Provider<MetaService> metaServiceProvider;
    private final ProcessorFilterAccess processorFilterAccess;
    private final DocumentAccess documentAccess;
    private final SecurityContext securityContext;

    @Inject
    StroomProcessingFacts(final Provider<ProcessingDiagnostics> processingDiagnosticsProvider,
                          final Provider<JobStates> jobStatesProvider,
                          final Provider<ClusterNodeManager> clusterNodeManagerProvider,
                          final Provider<NodeService> nodeServiceProvider,
                          final Provider<ProcessorFilterService> processorFilterServiceProvider,
                          final Provider<ProcessorTaskService> processorTaskServiceProvider,
                          final Provider<MetaService> metaServiceProvider,
                          final ProcessorFilterAccess processorFilterAccess,
                          final DocumentAccess documentAccess,
                          final SecurityContext securityContext) {
        this.processingDiagnosticsProvider = processingDiagnosticsProvider;
        this.jobStatesProvider = jobStatesProvider;
        this.clusterNodeManagerProvider = clusterNodeManagerProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.processorTaskServiceProvider = processorTaskServiceProvider;
        this.metaServiceProvider = metaServiceProvider;
        this.processorFilterAccess = processorFilterAccess;
        this.documentAccess = documentAccess;
        this.securityContext = securityContext;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public Settings settings() {
        return processingDiagnosticsProvider.get().getSettings();
    }

    @Override
    public Cluster cluster() {
        require(AppPermission.MANAGE_NODES_PERMISSION, "the cluster's nodes");
        final ClusterNodeManager clusterNodeManager = clusterNodeManagerProvider.get();
        final ClusterState state = Objects.requireNonNullElseGet(clusterNodeManager.getClusterState(),
                clusterNodeManager::getQuickClusterState);
        final NodeService nodeService = nodeServiceProvider.get();
        final List<Node> nodes = NullSafe.set(state.getAllNodes()).stream()
                .sorted(Comparator.comparingInt((String name) -> priority(nodeService, name)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .map(name -> new Node(name, state.isEnabled(name), state.isEnabledAndActive(name)))
                .toList();
        return new Cluster(nodes, state.getMasterNodeName());
    }

    @Override
    public Optional<JobState> job(final String jobName) {
        require(AppPermission.MANAGE_JOBS_PERMISSION, "jobs");
        return jobStatesProvider.get().getJob(jobName, NODE_TIMEOUT);
    }

    @Override
    public List<NodeClaim> claims() {
        require(AppPermission.VIEW_SYSTEM_INFO_PERMISSION, "what each node is claiming");
        return processingDiagnosticsProvider.get().getClaimStatus(NODE_TIMEOUT);
    }

    @Override
    public int deadTasks() {
        return processingDiagnosticsProvider.get().getDeadTaskCount();
    }

    @Override
    public List<ProcessorFilter> pipelineFilters(final DocRef pipeline) {
        final ProcessorFilterAccess.Filters found = processorFilterAccess.filtersOf(pipeline);
        if (found.refused() != null) {
            throw new PermissionException(securityContext.getUserRef(), found.refused());
        }
        return found.filters();
    }

    @Override
    public List<ProcessorFilter> filters(final boolean enabledOnly, final int limit) {
        requireManageProcessors();
        final ExpressionOperator.Builder expression = ExpressionOperator.builder()
                .addBooleanTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false);
        if (enabledOnly) {
            expression.addBooleanTerm(ProcessorFilterFields.ENABLED, Condition.EQUALS, true);
        }
        final ExpressionCriteria criteria = new ExpressionCriteria(expression.build());
        criteria.setPageRequest(new PageRequest(0, MAX_FILTERS_READ));
        final Map<DocRef, Boolean> viewable = new HashMap<>();
        return NullSafe.list(NullSafe.get(processorFilterServiceProvider.get().find(criteria),
                        ResultPage::getValues)).stream()
                .filter(filter -> !enabledOnly || filter.getProcessor() == null
                                  || (filter.getProcessor().isEnabled() && !filter.getProcessor().isDeleted()))
                // Only filters of pipelines the user can view, as the Processors tab shows them.
                .filter(filter -> {
                    // The filter's pipeline, from its processor when the filter doesn't name it.
                    final DocRef pipeline = filter.getPipeline();
                    return pipeline != null && pipeline.getUuid() != null
                           && viewable.computeIfAbsent(pipeline, documentAccess::canView);
                })
                .sorted(Comparator.comparingInt(ProcessorFilter::getPriority).reversed()
                        .thenComparing(ProcessorFilter::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public Backlogs backlogs(final int minPriority, final int limit) {
        requireManageProcessors();
        final ExpressionOperator.Builder statuses = ExpressionOperator.builder().op(Op.OR);
        for (final TaskStatus status : List.of(TaskStatus.CREATED, TaskStatus.QUEUED, TaskStatus.PROCESSING)) {
            statuses.addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, status.name());
        }
        final ExpressionCriteria criteria = new ExpressionCriteria(ExpressionOperator.builder()
                .addOperator(statuses.build())
                .addIntegerTerm(ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY, Condition.GREATER_THAN_OR_EQUAL_TO,
                        minPriority)
                .build());
        // One more than the most, to tell if there are more.
        criteria.setPageRequest(new PageRequest(0, limit + 1));
        final List<ProcessorTask> tasks = NullSafe.list(NullSafe.get(processorTaskServiceProvider.get().find(
                criteria), ResultPage::getValues));
        final Map<Integer, Map<TaskStatus, Long>> byFilter = new HashMap<>();
        tasks.stream()
                .limit(limit)
                .filter(task -> task.getProcessorFilter() != null && task.getStatus() != null)
                .forEach(task -> byFilter.computeIfAbsent(task.getProcessorFilter().getId(),
                                id -> new EnumMap<>(TaskStatus.class))
                        .merge(task.getStatus(), 1L, Long::sum));
        return new Backlogs(byFilter, tasks.size() > limit);
    }

    @Override
    public List<ProcessorTask> tasks(final int filterId, final TaskStatus status, final int limit) {
        requireManageProcessors();
        final ExpressionCriteria criteria = new ExpressionCriteria(ExpressionOperator.builder()
                .addIdTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID, Condition.EQUALS, filterId)
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, status.name())
                .build());
        criteria.setPageRequest(new PageRequest(0, limit));
        criteria.setSort(ProcessorTaskFields.CREATE_TIME_MS.getFldName(), false, false);
        return NullSafe.list(NullSafe.get(processorTaskServiceProvider.get().find(criteria), ResultPage::getValues));
    }

    @Override
    public List<ProcessorTask> tasksOfStream(final long metaId) {
        requireManageProcessors();
        final ExpressionCriteria criteria = new ExpressionCriteria(ExpressionOperator.builder()
                .addIdTerm(ProcessorTaskFields.META_ID, Condition.EQUALS, metaId)
                .build());
        criteria.setPageRequest(new PageRequest(0, 50));
        return NullSafe.list(NullSafe.get(processorTaskServiceProvider.get().find(criteria), ResultPage::getValues));
    }

    @Override
    public Optional<Meta> stream(final long metaId) {
        // find checks the user may view the stream's feed.
        final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                .build());
        criteria.setPageRequest(new PageRequest(0, 1));
        return NullSafe.list(NullSafe.get(metaServiceProvider.get().find(criteria), ResultPage::getValues)).stream()
                .findFirst();
    }

    @Override
    public Optional<Meta> newestStream(final String feed) {
        final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .build());
        criteria.setPageRequest(new PageRequest(0, 1));
        criteria.setSort(MetaFields.ID.getFldName(), true, false);
        return NullSafe.list(NullSafe.get(metaServiceProvider.get().find(criteria), ResultPage::getValues)).stream()
                .findFirst();
    }

    @Override
    public List<Long> locked(final List<Long> metaIds) {
        if (metaIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(NullSafe.set(metaServiceProvider.get().findLockedMeta(metaIds)));
    }

    @Override
    public Optional<Boolean> selects(final ProcessorFilter filter, final long metaId) {
        final QueryData queryData = filter.getQueryData();
        if (filter.isReprocess() || queryData == null || queryData.getExpression() == null
            || !isStreamStore(queryData.getDataSource())) {
            // A reprocess filter selects child streams, and a search filter a search's results: not told here.
            return Optional.empty();
        }
        try {
            // As task creation does: the filter's selection with any status terms dropped, for this one stream.
            final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder()
                    .addOperator(withoutStatus(queryData.getExpression()))
                    .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                    .build());
            criteria.setPageRequest(new PageRequest(0, 1));
            return Optional.of(!NullSafe.list(NullSafe.get(metaServiceProvider.get().find(criteria),
                    ResultPage::getValues)).isEmpty());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to test filter " + filter.getId() + " against stream " + metaId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean dependenciesReady(final ProcessorFilter filter) {
        final FeedDependencies dependencies = NullSafe.get(filter.getQueryData(), QueryData::getFeedDependencies);
        if (dependencies == null || NullSafe.isEmptyCollection(dependencies.getFeedDependencies())) {
            return true;
        }
        // Stroom's own reckoning: the epoch when a feed depended on has no Unlocked stream.
        final Instant effective = metaServiceProvider.get().getFeedDependencyEffectiveTime(
                dependencies.getFeedDependencies());
        return effective != null && effective.isAfter(Instant.EPOCH);
    }

    @Override
    public Optional<Profile> profile(final String name) {
        return processingDiagnosticsProvider.get().getProfile(name, now());
    }

    private static boolean isStreamStore(final DocRef dataSource) {
        return dataSource == null || MetaFields.STREAM_STORE_TYPE.equals(dataSource.getType());
    }

    // The expression with its Status terms removed, at any depth, as task creation removes them.
    private static ExpressionOperator withoutStatus(final ExpressionOperator operator) {
        final List<ExpressionItem> children = new ArrayList<>();
        for (final ExpressionItem item : NullSafe.list(operator.getChildren())) {
            if (item instanceof final ExpressionTerm term) {
                if (!MetaFields.STATUS.getFldName().equals(term.getField())) {
                    children.add(term);
                }
            } else if (item instanceof final ExpressionOperator child) {
                children.add(withoutStatus(child));
            }
        }
        return operator.copy().children(children).build();
    }

    private static int priority(final NodeService nodeService, final String name) {
        try {
            return nodeService.getPriority(name);
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    private void requireManageProcessors() {
        if (!processorFilterAccess.canManageProcessors()) {
            throw new PermissionException(securityContext.getUserRef(), "Manage Processors is needed");
        }
    }

    private void require(final AppPermission permission, final String what) {
        if (!securityContext.hasAppPermission(permission)) {
            throw new PermissionException(securityContext.getUserRef(), "You need the "
                                                                        + permission.getDisplayValue()
                                                                        + " permission to see " + what);
        }
    }
}
