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

import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.function.Function;
import java.util.function.Supplier;

public class AnalyticErrorWritingExecutor {

    private final TaskContextFactory taskContextFactory;
    private final Provider<AnalyticErrorWriter> analyticErrorWriterProvider;

    @Inject
    AnalyticErrorWritingExecutor(final TaskContextFactory taskContextFactory,
                                 final Provider<AnalyticErrorWriter> analyticErrorWriterProvider) {
        this.taskContextFactory = taskContextFactory;
        this.analyticErrorWriterProvider = analyticErrorWriterProvider;
    }

    <R> Supplier<R> wrap(final String taskName,
                         final String errorFeedName,
                         final String pipelineUuid,
                         final TaskContext parentTaskContext,
                         final Function<TaskContext, R> function) {
        return taskContextFactory.childContextResult(
                parentTaskContext,
                taskName,
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    final AnalyticErrorWriter analyticErrorWriter = analyticErrorWriterProvider.get();
                    return analyticErrorWriter.exec(
                            errorFeedName,
                            pipelineUuid,
                            taskContext,
                            function);
                });
    }
}
