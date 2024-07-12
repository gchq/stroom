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
