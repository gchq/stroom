/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionTracker;
import stroom.docref.DocRef;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.Severity;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Defines the interface for a schedulable executable item.
 *
 * <p>
 * Implementations provide:
 *     <ul>
 *         <li>Documents to be scheduled</li>
 *         <li>Execution logic</li>
 *         <li>Identity and logging behaviour</li>
 *         <li>Optional lifecycle hooks</li>
 *     </ul>
 *
 *     <p>
 *         This interface is used to orchestrate execution, scheduling, security context
 *         switching, and execution tracking.
 *     </p>
 * </p>
 *
 * @param <T> The document type being scheduled.
 */
public interface ScheduledExecutable<T> {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledExecutable.class);

    /**
     * Performs the core execution logic for a scheduled document.
     *
     * <p>
     * This method is responsible for performing the scheduled work and returning
     * an updated {@link ScheduledExecutorService.ExecutionResult}.
     * </p>
     *
     * @param doc                    The document being executed.
     * @param trigger                The trigger defining the schedule.
     * @param executionTime          The actual execution time.
     * @param effectiveExecutionTime The logical execution time for the schedule.
     * @param executionSchedule      The execution schedule.
     * @param currentTracker         The current execution tracker, if any.
     * @param executionResult        The current execution result.
     * @return The updated execution result.
     */
    ExecutionResult run(T doc,
                        Trigger trigger,
                        Instant executionTime,
                        Instant effectiveExecutionTime,
                        ExecutionSchedule executionSchedule,
                        ExecutionTracker currentTracker,
                        ExecutionResult executionResult);

    /**
     * Returns the document reference associated with the given document.
     *
     * @param doc The document.
     * @return The document reference.
     */
    DocRef getDocRef(T doc);

    /**
     * Reloads the document prior to execution.
     *
     * @param docRef The doc ref.
     * @return The reloaded document, or {@code null} if it no longer exists.
     */
    T load(DocRef docRef);

    /**
     * Reloads the document prior to execution.
     *
     * @param doc The existing document.
     * @return The reloaded document, or {@code null} if it no longer exists.
     */
    T reload(T doc);

    /**
     * returns a human-readable identity string for the document.
     *
     * @param doc The document.
     * @return An identity string.
     */
    String getIdentity(T doc);

    /**
     * Returns all documents eligible for scheduled execution.
     *
     * @return The process type.
     */
    List<T> getDocs();

    /**
     * Returns a short name describing the type of process being executed/
     *
     * @return The process type.
     */
    String getProcessType();

    /**
     * Logs an execution related message.
     *
     * @param severity The severity level.
     * @param message  The message to log.
     * @param e        An optional exception.
     */
    default void log(final Severity severity,
                     final String message,
                     final Throwable e) {
        LOGGER.error(message, e);
    }

    /**
     * Determines whether the given document should be executed.
     *
     * @param doc The document.
     * @return {@code true} if execution should proceed.
     */
    default boolean shouldRun(final T doc) {
        return true;
    }

    /**
     * Hook invoked immediately before execution.
     * <p>
     * Implementations may override this to wrap execution in additional behaviour.
     * </p>
     *
     * @param doc                    The document being executed.
     * @param trigger                The trigger defining the schedule.
     * @param executionTime          The actual execution time.
     * @param effectiveExecutionTime The logical execution time.
     * @param executionSchedule      The execution schedule.
     * @param currentTracker         The current execution tracker, if any.
     * @param taskContext            The task context.
     * @param function               The execution function.
     */
    default void beforeProcess(final T doc,
                               final Trigger trigger,
                               final Instant executionTime,
                               final Instant effectiveExecutionTime,
                               final ExecutionSchedule executionSchedule,
                               final ExecutionTracker currentTracker,
                               final TaskContext taskContext,
                               final Function<TaskContext, T> function) {
        function.apply(taskContext);
    }

    /**
     * Called after all scheduled executions have completed.
     *
     * @param analyticDocs The list of all documents processed by this executor.
     */
    default void postExecuteTidyUp(final List<T> analyticDocs) {

    }
}
