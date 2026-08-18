/*
 * Copyright 2024 Crown Copyright
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

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ExecutionTracker;
import stroom.analytics.shared.ReportDoc;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.FindUserContext;
import stroom.security.user.api.UserRefLookup;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.ui.config.shared.ReportUiDefaultConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoLogged(OperationType.UNLOGGED)
class ExecutionScheduleResourceImpl implements ExecutionScheduleResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExecutionScheduleResourceImpl.class);

    private final Provider<ExecutionScheduleDao> executionScheduleDaoProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<UserRefLookup> userRefLookupProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<AnalyticRuleStore> analyticRuleStoreProvider;
    private final Provider<ReportStore> reportStoreProvider;
    private final Provider<AnalyticUiDefaultConfig> analyticUiDefaultConfigProvider;
    private final Provider<ReportUiDefaultConfig> reportUiDefaultConfigProvider;

    @Inject
    ExecutionScheduleResourceImpl(final Provider<ExecutionScheduleDao> executionScheduleDaoProvider,
                                  final Provider<SecurityContext> securityContextProvider,
                                  final Provider<UserRefLookup> userRefLookupProvider,
                                  final Provider<Map<ExecuteNowType, ExecuteNow>> executeNowMapProvider,
                                  final Provider<NodeService> nodeServiceProvider,
                                  final Provider<AnalyticRuleStore> analyticRuleStoreProvider,
                                  final Provider<ReportStore> reportStoreProvider,
                                  final Provider<AnalyticUiDefaultConfig> analyticUiDefaultConfigProvider,
                                  final Provider<ReportUiDefaultConfig> reportUiDefaultConfigProvider) {
        this.executionScheduleDaoProvider = executionScheduleDaoProvider;
        this.securityContextProvider = securityContextProvider;
        this.userRefLookupProvider = userRefLookupProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.analyticRuleStoreProvider = analyticRuleStoreProvider;
        this.reportStoreProvider = reportStoreProvider;
        this.analyticUiDefaultConfigProvider = analyticUiDefaultConfigProvider;
        this.reportUiDefaultConfigProvider = reportUiDefaultConfigProvider;
    }

    @Override
    public ExecutionSchedule createExecutionSchedule(final ExecutionSchedule executionSchedule) {
        validate(executionSchedule);
        return executionScheduleDaoProvider.get().createExecutionSchedule(checkRunAs(executionSchedule));
    }

    @Override
    public ExecutionSchedule updateExecutionSchedule(final ExecutionSchedule executionSchedule) {
        validate(executionSchedule);
        return executionScheduleDaoProvider.get().updateExecutionSchedule(checkRunAs(executionSchedule));
    }

    @Override
    public Boolean deleteExecutionSchedule(final ExecutionSchedule executionSchedule) {
        return executionScheduleDaoProvider.get().deleteExecutionSchedule(executionSchedule);
    }

    @Override
    public Boolean deleteExecutionSchedules(final List<ExecutionSchedule> executionSchedules) {
        return executionScheduleDaoProvider.get().deleteExecutionSchedules(executionSchedules);
    }

    @Override
    public ResultPage<ExecutionSchedule> fetchExecutionSchedule(final ExecutionScheduleRequest request) {
        return executionScheduleDaoProvider.get().fetchExecutionSchedule(request);
    }

    @Override
    public ResultPage<ExecutionHistory> fetchExecutionHistory(final ExecutionHistoryRequest request) {
        return executionScheduleDaoProvider.get().fetchExecutionHistory(request);
    }

    @Override
    public ExecutionTracker fetchTracker(final ExecutionSchedule schedule) {
        return executionScheduleDaoProvider.get().fetchTracker(schedule);
    }

    @Override
    public Boolean executeSchedulesNow(final List<ExecutionSchedule> schedules) {
        return executionScheduleDaoProvider.get().executeSchedulesNow(schedules);
    }

    private void validate(final ExecutionSchedule executionSchedule) {
        // A node is required whether or not the schedule is enabled because the column is not nullable, and a new
        // schedule is built without one, so check it up front to give a usable message rather than letting the
        // user see a constraint violation.
        if (NullSafe.isBlankString(executionSchedule.getNodeName())) {
            throw new RuntimeException("A processing node must be selected for this schedule.");
        }

        // Everything else only matters once the schedule runs, so a disabled schedule is allowed to be incomplete.
        // That is what lets a rule be created and worked on before its feeds have been chosen.
        if (executionSchedule.isEnabled()) {
            checkCanBeEnabled(executionSchedule);
        }
    }

    /**
     * An enabled schedule that has no node to run on, or no feed to write its errors to, produces nothing and
     * cannot report why. Refuse to enable it rather than letting it fail invisibly.
     */
    private void checkCanBeEnabled(final ExecutionSchedule executionSchedule) {
        // A schedule only ever runs on the node it names, so one naming a node that no longer exists would never
        // be picked up by any node and would fail silently.
        final String nodeName = executionSchedule.getNodeName();
        final List<String> nodeNames = nodeServiceProvider.get().findNodeNames(new FindNodeCriteria());
        if (!nodeNames.contains(nodeName)) {
            throw new RuntimeException("This schedule cannot be enabled because node '" + nodeName +
                                       "' no longer exists. Available nodes are: " +
                                       String.join(", ", nodeNames) + ".");
        }

        if (getErrorFeed(executionSchedule.getOwningDoc()) == null) {
            throw new RuntimeException("This schedule cannot be enabled because no error feed has been set on '" +
                                       executionSchedule.getOwningDoc().getName() +
                                       "' and no default error feed has been configured.");
        }
    }

    /**
     * Resolve the error feed the same way the executor does, i.e. the one set on the owning document, falling back
     * to the configured default.
     */
    private DocRef getErrorFeed(final DocRef owningDoc) {
        if (owningDoc == null) {
            return null;
        }

        final AbstractAnalyticRuleDoc doc;
        final DocRef defaultErrorFeed;
        if (ReportDoc.TYPE.equals(owningDoc.getType())) {
            doc = reportStoreProvider.get().readDocument(owningDoc);
            defaultErrorFeed = reportUiDefaultConfigProvider.get().getDefaultErrorFeed();
        } else {
            doc = analyticRuleStoreProvider.get().readDocument(owningDoc);
            defaultErrorFeed = analyticUiDefaultConfigProvider.get().getDefaultErrorFeed();
        }

        final DocRef errorFeed = doc == null
                ? null
                : doc.getErrorFeed();
        return errorFeed != null
                ? errorFeed
                : defaultErrorFeed;
    }

    private ExecutionSchedule checkRunAs(final ExecutionSchedule executionSchedule) {
        final SecurityContext securityContext = securityContextProvider.get();
        final UserRef currentUser = securityContext.getUserRef();
        if (executionSchedule.getRunAsUser() == null) {
            return executionSchedule
                    .copy()
                    .runAsUser(currentUser)
                    .build();
        } else {
            final Optional<UserRef> userRef = userRefLookupProvider.get()
                    .getByUuid(executionSchedule.getRunAsUser().getUuid(), FindUserContext.RUN_AS);
            if (userRef.isEmpty()) {
                throw new PermissionException(currentUser, "You do not have permission to execute as " +
                                                           executionSchedule.getRunAsUser().toDisplayString());
            }
        }
        return executionSchedule;
    }
}
