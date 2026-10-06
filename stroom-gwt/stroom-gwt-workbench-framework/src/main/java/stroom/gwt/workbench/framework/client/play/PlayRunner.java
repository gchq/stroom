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

package stroom.gwt.workbench.framework.client.play;

import stroom.gwt.workbench.framework.client.play.PlayStep.Kind;

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// Runs a story's play function in the preview, reporting each step's progress so the
/// Interactions addon can show it. It supports the addon's debugger controls by re-rendering the
/// story and replaying the steps up to a chosen point.
///
/// Every step, including each step inside a `step(...)` group, runs after a short pause so the
/// user can see each one happen.
///
/// From [#start()] until [#dispose()], i.e. for the whole life of the story's renderings, errors
/// the story's own code throws (e.g. in an event handler or timer) are caught, as an unhandled
/// error fails a React play:
///
/// * while the steps run (or the story re-renders for a rerun), the error fails the step that is
///   running (or the next to run, or the run if no step is left);
/// * at any other time, e.g. after the run has completed, while it is paused by the debugger
///   controls, after it has been stopped, or for a story without steps, the run is marked
///   [RunStatus#ERRORED] at once and the error is shown as an extra entry at the end of the
///   steps.
///
/// An error in the runner itself, e.g. a step's description failing, ends the run as
/// [RunStatus#ERRORED] with the error's message, rather than leaving it stuck as
/// [RunStatus#PLAYING].
public class PlayRunner {

    /// How long a `waitFor(...)` waits between tries.
    static final int WAIT_FOR_INTERVAL_MILLIS = 50;
    /// The pause before each step so the user can see each one happen.
    static final int STEP_DELAY_MILLIS = 20;
    /// The text of the entry added for an error thrown by the story's code when no step is running.
    static final String UNHANDLED_ERROR_TEXT = "Unhandled error in the story";
    /// The text of the entry added for an error in the runner itself.
    static final String RUNNER_ERROR_TEXT = "Error in the play runner";

    private static final String UNCAUGHT_PREFIX = "Uncaught error in the story: ";

    private final List<PlayStep> topLevelSteps;
    private final Element root;
    private final BooleanSupplier rerender;
    private final Listener listener;
    private final Environment environment;
    private final List<LogEntry> entries = new ArrayList<>();
    private final Map<PlayStep, LogEntry> entriesByStep = new IdentityHashMap<>();

    private int nextStep;
    private int targetStep;
    private boolean running;
    // True while the story re-renders for a rerun, rewind or step back
    private boolean rendering;
    private boolean failed;
    // Incremented on each restart so steps from an earlier run are abandoned
    private int generation;
    // The pending delay before a step, or before retrying a waitFor, so it can be cancelled
    private Cancellable timer;
    // True while errors thrown by the story's code are being caught
    private boolean catchingErrors;
    // An error thrown by the story's code while the steps run, not yet reported
    private String uncaughtError;
    // The entry for an error that wasn't reported by a step, shown after the steps, or null
    private LogEntry errorEntry;

    /// @param play     The play function's steps. No more steps can be added to it. It may have
    ///                 no steps, e.g. for a story without a play function, so that errors the
    ///                 story throws are still reported.
    /// @param root     The element the story is rendered in.
    /// @param rerender Renders the story again from scratch, returning false if it failed (the
    ///                 failure being reported by the caller), in which case no steps run.
    /// @param listener Told about progress.
    public PlayRunner(final Play play,
                      final Element root,
                      final BooleanSupplier rerender,
                      final Listener listener) {
        this(play, root, rerender, listener, new GwtPlayEnvironment());
    }

    /// @param play        The play function's steps. No more steps can be added to it.
    /// @param root        The element the story is rendered in.
    /// @param rerender    Renders the story again, returning false if it failed.
    /// @param listener    Told about progress.
    /// @param environment Provides timers, the time and error catching.
    PlayRunner(final Play play,
               final Element root,
               final BooleanSupplier rerender,
               final Listener listener,
               final Environment environment) {
        play.finishBuilding();
        this.topLevelSteps = new ArrayList<>(play.getSteps());
        this.root = root;
        this.rerender = rerender;
        this.listener = listener;
        this.environment = environment;
        topLevelSteps.forEach(step -> flatten(step, 0));
    }

    private void flatten(final PlayStep step, final int depth) {
        final LogEntry entry = new LogEntry(step, depth);
        entries.add(entry);
        entriesByStep.put(step, entry);
        step.getChildren().forEach(child -> flatten(child, depth + 1));
    }

    /// Runs all the steps against the story as it is now rendered, and starts catching the
    /// errors the story's code throws (until [#dispose()]).
    public void start() {
        guard(() -> {
            catchErrors();
            restart(topLevelSteps.size(), false);
        });
    }

    /// Handles one of the Interactions addon's debugger controls.
    ///
    /// @param control The control.
    public void control(final Control control) {
        guard(() -> {
            switch (control) {
                case REWIND:
                    restart(0, true);
                    break;
                case BACK:
                    restart(Math.max(0, nextStep - 1), true);
                    break;
                case NEXT:
                    if (!running && !failed && nextStep < topLevelSteps.size()) {
                        targetStep = nextStep + 1;
                        startRunning();
                        runNext(generation);
                    }
                    break;
                case END:
                    if (!running && !failed) {
                        targetStep = topLevelSteps.size();
                        startRunning();
                        runNext(generation);
                    }
                    break;
                case RERUN:
                default:
                    restart(topLevelSteps.size(), true);
                    break;
            }
        });
    }

    /// Stops running steps, e.g. because the story has been re-rendered with new args. The
    /// debugger controls can carry on from the next step. Errors the story throws are still
    /// caught, and now mark the run as errored.
    public void stop() {
        guard(() -> {
            generation++;
            cancelTimer();
            if (running) {
                running = false;
                finishRun();
            }
        });
    }

    /// Stops running steps and stops catching the errors the story's code throws, e.g. when the
    /// preview is unloaded.
    public void dispose() {
        generation++;
        cancelTimer();
        running = false;
        if (catchingErrors) {
            catchingErrors = false;
            environment.stopCatchingErrors();
        }
    }

    /// Reports an error that happened outside the story's own code but belongs to its rendering,
    /// e.g. a clean up of the previous rendering that failed, as an error thrown by the story's
    /// code is reported: it fails the running (or next) step, or marks the run as errored.
    ///
    /// @param message The error's message.
    public void reportError(final String message) {
        guard(() -> onUncaughtError(message));
    }

    private void restart(final int target, final boolean render) {
        generation++;
        cancelTimer();
        running = false;
        PlayStep.startRun();
        nextStep = 0;
        targetStep = target;
        failed = false;
        uncaughtError = null;
        errorEntry = null;
        // Show the steps still to run, as described before they run
        entries.forEach(entry -> entry.reset(entry.step.describe(root)));
        if (render) {
            final boolean rendered;
            rendering = true;
            try {
                rendered = rerender.getAsBoolean();
            } finally {
                rendering = false;
            }
            if (!rendered) {
                // The story failed to render, which the caller has reported, so there's nothing
                // to run the steps against
                failed = true;
                uncaughtError = null;
                return;
            }
        }
        startRunning();
        report();
        runNext(generation);
    }

    private void catchErrors() {
        if (!catchingErrors) {
            catchingErrors = true;
            environment.catchErrors(message -> guard(() -> onUncaughtError(message)));
        }
    }

    private void startRunning() {
        running = true;
        catchErrors();
    }

    private void onUncaughtError(final String message) {
        if (running || rendering) {
            // Fails the running step, or the next to run
            if (uncaughtError == null) {
                uncaughtError = message;
            }
        } else if (!failed) {
            // E.g. after the run has completed or while it is paused
            failRun(UNHANDLED_ERROR_TEXT, UNCAUGHT_PREFIX + message);
        }
    }

    /// Ends the run as errored, showing the error as an extra entry after the steps.
    private void failRun(final String text, final String error) {
        generation++;
        cancelTimer();
        running = false;
        failed = true;
        uncaughtError = null;
        if (errorEntry == null) {
            errorEntry = new LogEntry(null, 0);
            errorEntry.text = text;
            errorEntry.status = Status.ERROR;
            errorEntry.error = error;
        }
        report();
    }

    /// Reports the end of a run (or of the steps run so far), failing it if the story's code has
    /// thrown an error since the last step.
    private void finishRun() {
        if (!failed && uncaughtError != null) {
            failRun(UNHANDLED_ERROR_TEXT, uncaughtErrorMessage());
        } else {
            uncaughtError = null;
            report();
        }
    }

    /// Runs some of the runner's own code, ending the run as errored if it throws, so the run
    /// can't be left stuck as playing.
    private void guard(final Runnable code) {
        try {
            code.run();
        } catch (final Throwable e) {
            try {
                failRun(RUNNER_ERROR_TEXT, describe(e));
            } catch (final Throwable again) {
                // Reporting failed too, e.g. the listener is broken, so there's nothing more to do
                // than make sure the runner is stopped
                running = false;
                failed = true;
            }
        }
    }

    private void runNext(final int runGeneration) {
        if (runGeneration != generation) {
            return;
        }
        if (failed || nextStep >= targetStep) {
            running = false;
            finishRun();
            return;
        }
        final PlayStep step = topLevelSteps.get(nextStep);
        schedule(STEP_DELAY_MILLIS, runGeneration, () -> execute(step, runGeneration, success -> {
            if (runGeneration != generation) {
                return;
            }
            if (success) {
                nextStep++;
            } else {
                failed = true;
            }
            runNext(runGeneration);
        }));
    }

    /// Runs a task after a delay, unless the runner has been restarted or stopped by then.
    private void schedule(final int delayMillis, final int runGeneration, final Runnable task) {
        cancelTimer();
        timer = environment.schedule(delayMillis, () -> guard(() -> {
            timer = null;
            if (runGeneration == generation) {
                task.run();
            }
        }));
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void execute(final PlayStep step, final int runGeneration, final Callback callback) {
        if (runGeneration != generation) {
            return;
        }
        final LogEntry entry = entriesByStep.get(step);
        entry.status = Status.ACTIVE;
        entry.text = step.describe(root);
        report();
        // An error thrown by the story's code since the last step, e.g. by a timer
        if (failOnUncaughtError(entry)) {
            callback.done(false);
            return;
        }

        if (step.getKind() == Kind.ACTION) {
            try {
                step.run(root);
            } catch (final Throwable e) {
                fail(entry, e);
                callback.done(false);
                return;
            }
            // An error thrown by the story's code while the step ran, e.g. in an event handler
            if (failOnUncaughtError(entry)) {
                callback.done(false);
                return;
            }
            entry.text = step.describe(root);
            entry.status = Status.DONE;
            report();
            callback.done(true);
        } else if (step.getKind() == Kind.STEP) {
            executeChildren(step.getChildren(), 0, runGeneration, success -> {
                entry.status = success
                        ? Status.DONE
                        : Status.ERROR;
                report();
                callback.done(success);
            });
        } else if (step.getKind() == Kind.SLEEP) {
            schedule(step.getTimeoutMillis(), runGeneration, () -> {
                if (failOnUncaughtError(entry)) {
                    callback.done(false);
                    return;
                }
                entry.status = Status.DONE;
                report();
                callback.done(true);
            });
        } else {
            waitFor(step, entry, environment.now(), runGeneration, callback);
        }
    }

    /// Runs the steps in a `step(...)` group one after another, with the same pause before each
    /// as before a top level step.
    private void executeChildren(final List<PlayStep> children,
                                 final int index,
                                 final int runGeneration,
                                 final Callback callback) {
        if (runGeneration != generation) {
            return;
        }
        if (index >= children.size()) {
            callback.done(true);
            return;
        }
        schedule(STEP_DELAY_MILLIS, runGeneration, () -> execute(children.get(index), runGeneration, success -> {
            if (success) {
                executeChildren(children, index + 1, runGeneration, callback);
            } else {
                callback.done(false);
            }
        }));
    }

    /// Runs the steps in the group (including any nested groups) synchronously, retrying until
    /// they all pass or the group's timeout runs out. An error thrown by the story's code fails
    /// the group at once, without retrying.
    private void waitFor(final PlayStep step,
                         final LogEntry entry,
                         final double startTime,
                         final int runGeneration,
                         final Callback callback) {
        if (runGeneration != generation) {
            return;
        }
        Throwable failure = null;
        try {
            for (final PlayStep child : step.getChildren()) {
                child.runAll(root, new PlayStep.Listener() {
                    @Override
                    public void started(final PlayStep started) {
                        final LogEntry startedEntry = entriesByStep.get(started);
                        startedEntry.text = started.describe(root);
                        startedEntry.status = Status.ACTIVE;
                    }

                    @Override
                    public void passed(final PlayStep passed) {
                        final LogEntry passedEntry = entriesByStep.get(passed);
                        passedEntry.text = passed.describe(root);
                        passedEntry.status = Status.DONE;
                    }
                });
            }
        } catch (final Throwable e) {
            failure = e;
        }
        if (uncaughtError != null) {
            final String error = uncaughtErrorMessage();
            failActiveDescendants(step, error);
            fail(entry, error);
            callback.done(false);
        } else if (failure == null) {
            entry.status = Status.DONE;
            report();
            callback.done(true);
        } else if (environment.now() - startTime < step.getTimeoutMillis()) {
            report();
            schedule(WAIT_FOR_INTERVAL_MILLIS, runGeneration,
                    () -> waitFor(step, entry, startTime, runGeneration, callback));
        } else {
            final String error = describe(failure);
            failActiveDescendants(step, error);
            fail(entry, error);
            callback.done(false);
        }
    }

    /// Fails the step if the story's code has thrown an error since the last check.
    ///
    /// @return True if it failed.
    private boolean failOnUncaughtError(final LogEntry entry) {
        if (uncaughtError == null) {
            return false;
        }
        fail(entry, uncaughtErrorMessage());
        return true;
    }

    private String uncaughtErrorMessage() {
        final String message = "Uncaught error in the story: " + uncaughtError;
        uncaughtError = null;
        return message;
    }

    /// Marks the steps within a group that were running when it failed as failed.
    private void failActiveDescendants(final PlayStep group, final String error) {
        for (final PlayStep child : group.getChildren()) {
            final LogEntry childEntry = entriesByStep.get(child);
            if (childEntry.status == Status.ACTIVE) {
                fail(childEntry, error);
            }
            failActiveDescendants(child, error);
        }
    }

    private void fail(final LogEntry entry, final Throwable e) {
        fail(entry, describe(e));
    }

    private void fail(final LogEntry entry, final String error) {
        entry.status = Status.ERROR;
        entry.error = error;
        report();
    }

    private static String describe(final Throwable e) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.toString();
    }

    private void report() {
        final RunStatus status;
        if (failed) {
            status = RunStatus.ERRORED;
        } else if (running) {
            status = RunStatus.PLAYING;
        } else if (nextStep < topLevelSteps.size()) {
            status = RunStatus.PAUSED;
        } else {
            status = RunStatus.COMPLETED;
        }
        final List<LogEntry> reported;
        if (errorEntry != null) {
            reported = new ArrayList<>(entries);
            reported.add(errorEntry);
        } else {
            reported = entries;
        }
        listener.onUpdate(status, reported, nextStep, topLevelSteps.size());
    }


    // --------------------------------------------------------------------------------


    /// The state of a step.
    public enum Status {
        /// Not run yet.
        WAITING,
        /// Running.
        ACTIVE,
        /// Passed.
        DONE,
        /// Failed.
        ERROR
    }


    // --------------------------------------------------------------------------------


    /// The state of the whole play function.
    public enum RunStatus {
        /// Steps are running.
        PLAYING,
        /// Stopped part way by the debugger controls.
        PAUSED,
        /// All steps passed.
        COMPLETED,
        /// A step failed.
        ERRORED
    }


    // --------------------------------------------------------------------------------


    /// The Interactions addon's debugger controls.
    public enum Control {
        /// Go to the start, i.e. re-render without running any steps.
        REWIND,
        /// Go back a step.
        BACK,
        /// Run the next step.
        NEXT,
        /// Run the remaining steps.
        END,
        /// Re-render and run all the steps.
        RERUN;

        /// @param name The name of a control, e.g. `REWIND`, as sent by the manager.
        /// @return The control, or null if the name is null or not a control.
        public static Control fromName(final String name) {
            for (final Control control : values()) {
                if (control.name().equals(name)) {
                    return control;
                }
            }
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// A step as shown in the Interactions addon, or the extra entry after the steps for an error
    /// that no step reported.
    public static final class LogEntry {

        private final PlayStep step;
        private final int depth;
        private String text;
        private Status status = Status.WAITING;
        private String error;

        private LogEntry(final PlayStep step, final int depth) {
            this.step = step;
            this.depth = depth;
        }

        private void reset(final String text) {
            this.text = text;
            this.status = Status.WAITING;
            this.error = null;
        }

        /// @return How deeply the step is nested in `step` or `waitFor` groups.
        public int getDepth() {
            return depth;
        }

        /// @return The step as code, e.g. `userEvent.click(...)`, or for the extra entry of an
        /// error thrown when no step was running, [#UNHANDLED_ERROR_TEXT].
        public String getText() {
            return text;
        }

        /// @return The state of the step.
        public Status getStatus() {
            return status;
        }

        /// @return Why the step failed, or null.
        public String getError() {
            return error;
        }
    }


    // --------------------------------------------------------------------------------


    /// Told about the progress of the play function.
    public interface Listener {

        /// @param status    The state of the whole play function.
        /// @param entries   Every step, including nested steps, in order.
        /// @param nextStep  The index of the next top level step to run.
        /// @param stepCount The number of top level steps.
        void onUpdate(RunStatus status, List<LogEntry> entries, int nextStep, int stepCount);
    }


    // --------------------------------------------------------------------------------


    /// What the runner needs from the browser, so it can be tested on the JVM.
    interface Environment {

        /// @return The current time in milliseconds.
        double now();

        /// @param delayMillis How long to wait.
        /// @param task        What to do then.
        /// @return A way to cancel the task.
        Cancellable schedule(int delayMillis, Runnable task);

        /// Starts catching the errors the story's code throws, e.g. in event handlers and timers,
        /// which would otherwise only be logged.
        ///
        /// @param onError Given each error's message.
        void catchErrors(Consumer<String> onError);

        /// Stops catching errors, restoring how they were handled before.
        void stopCatchingErrors();
    }


    // --------------------------------------------------------------------------------


    /// A scheduled task that can be cancelled.
    interface Cancellable {

        /// Cancels the task if it hasn't run yet.
        void cancel();
    }


    // --------------------------------------------------------------------------------


    private interface Callback {

        void done(boolean success);
    }
}
