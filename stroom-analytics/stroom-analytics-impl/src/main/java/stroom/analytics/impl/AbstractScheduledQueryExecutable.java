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
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.task.api.TaskContext;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.Severity;

import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

abstract class AbstractScheduledQueryExecutable<T extends AbstractAnalyticRuleDoc>
        implements ScheduledExecutable<T> {

    private final Provider<AnalyticErrorWriter> analyticErrorWriterProvider;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;

    AbstractScheduledQueryExecutable(final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                                     final Provider<ErrorReceiverProxy> errorReceiverProxyProvider) {
        this.analyticErrorWriterProvider = analyticErrorWriterProvider;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
    }

    @Override
    public void log(final Severity severity, final String message, final Throwable e) {
        errorReceiverProxyProvider.get()
                .getErrorReceiver()
                .log(severity, null, null, message, e);
    }

    @Override
    public boolean shouldRun(final T doc) {
        return AnalyticProcessType.SCHEDULED_QUERY.equals(doc.getAnalyticProcessType());
    }

    @Override
    public void beforeProcess(final T doc,
                              final Trigger trigger,
                              final Instant executionTime,
                              final Instant effectiveExecutionTime,
                              final ExecutionSchedule executionSchedule,
                              final ExecutionTracker currentTracker,
                              final TaskContext taskContext,
                              final Function<TaskContext, T> function) {
        final String errorFeedName = getErrorFeedName(doc);
        final AnalyticErrorWriter analyticErrorWriter = analyticErrorWriterProvider.get();
        analyticErrorWriter.exec(
                errorFeedName,
                null,
                taskContext,
                function);
    }

    abstract String getErrorFeedName(T doc);

    @Override
    public List<T> getDocs() {
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
