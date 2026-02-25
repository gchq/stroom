/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionTracker;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.node.api.NodeInfo;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.Severity;

import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

abstract class AbstractScheduledQueryExecutor<T extends AbstractAnalyticRuleDoc>
        extends ScheduledExecutorService<T>
        implements HasUserDependencies {

    final private Provider<AnalyticErrorWriter> analyticErrorWriterProvider;
    final private Provider<ErrorReceiverProxy> errorReceiverProxyProvider;

    AbstractScheduledQueryExecutor(final ExecutorProvider executorProvider,
                                   final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                                   final TaskContextFactory taskContextFactory,
                                   final NodeInfo nodeInfo,
                                   final SecurityContext securityContext,
                                   final ExecutionScheduleDao executionScheduleDao,
                                   final Provider<DocRefInfoService> docRefInfoServiceProvider,
                                   final String processType,
                                   final Provider<ErrorReceiverProxy> errorReceiverProxyProvider) {
        super(executorProvider,
                taskContextFactory,
                nodeInfo,
                securityContext,
                executionScheduleDao,
                docRefInfoServiceProvider,
                processType);

        this.analyticErrorWriterProvider = analyticErrorWriterProvider;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
    }

    @Override
    protected void log(final Severity severity, final String message, final Throwable e) {
        errorReceiverProxyProvider.get()
                .getErrorReceiver()
                .log(severity, null, null, message, e);
    }

    @Override
    boolean shouldRun(final T doc) {
        return AnalyticProcessType.SCHEDULED_QUERY.equals(doc.getAnalyticProcessType());
    }

    @Override
    void outerProcess(final T doc,
                      final Trigger trigger,
                      final Instant executionTime,
                      final Instant effectiveExecutionTime,
                      final ExecutionSchedule executionSchedule,
                      final ExecutionTracker currentTracker,
                      final TaskContext taskContext) {
        final String errorFeedName = getErrorFeedName(doc);
        final AnalyticErrorWriter analyticErrorWriter = analyticErrorWriterProvider.get();
        analyticErrorWriter.exec(
                errorFeedName,
                null,
                taskContext,
                (t) -> process(
                        doc,
                        trigger,
                        executionTime,
                        effectiveExecutionTime,
                        executionSchedule,
                        currentTracker));
    }

    abstract String getErrorFeedName(T doc);

    @Override
    protected List<T> getDocs() {
        final List<T> list = new ArrayList<>();
        final List<T> rules = getRules();
        for (final T rule : rules) {
            if (AnalyticProcessType.SCHEDULED_QUERY.equals(rule.getAnalyticProcessType())) {
                list.add(rule);
            }
        }
        return list;
    }

    abstract List<T> getRules();
}
