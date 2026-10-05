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

import com.google.gwt.core.client.Duration;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// Runs a story's play function in the preview, reporting each step's progress so the
/// Interactions addon can show it. It supports the addon's debugger controls by re-rendering the
/// story and replaying the steps up to a chosen point.
public class PlayRunner {

    private static final int WAIT_FOR_INTERVAL_MILLIS = 50;
    // A short pause between steps so the user can see each one happen
    private static final int STEP_DELAY_MILLIS = 20;

    private final List<PlayStep> topLevelSteps;
    private final Element root;
    private final Runnable rerender;
    private final Listener listener;
    private final List<LogEntry> entries = new ArrayList<>();
    private final Map<PlayStep, LogEntry> entriesByStep = new IdentityHashMap<>();

    private int nextStep;
    private int targetStep;
    private boolean running;
    private boolean failed;
    // Incremented on each restart so steps from an earlier run are abandoned
    private int generation;
    // The pending delay before a step, or before retrying a waitFor, so it can be cancelled
    private Timer timer;

    /// @param play     The play function's steps.
    /// @param root     The element the story is rendered in.
    /// @param rerender Renders the story again from scratch.
    /// @param listener Told about progress.
    public PlayRunner(final Play play,
                      final Element root,
                      final Runnable rerender,
                      final Listener listener) {
        this.topLevelSteps = new ArrayList<>(play.getSteps());
        this.root = root;
        this.rerender = rerender;
        this.listener = listener;
        topLevelSteps.forEach(step -> flatten(step, 0));
    }

    private void flatten(final PlayStep step, final int depth) {
        final LogEntry entry = new LogEntry(step, depth);
        entries.add(entry);
        entriesByStep.put(step, entry);
        step.getChildren().forEach(child -> flatten(child, depth + 1));
    }

    /// Runs all the steps against the story as it is now rendered.
    public void start() {
        restart(topLevelSteps.size(), false);
    }

    /// Handles one of the Interactions addon's debugger controls.
    ///
    /// @param control The control.
    public void control(final Control control) {
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
                    running = true;
                    runNext(generation);
                }
                break;
            case END:
                if (!running && !failed) {
                    targetStep = topLevelSteps.size();
                    running = true;
                    runNext(generation);
                }
                break;
            case RERUN:
            default:
                restart(topLevelSteps.size(), true);
                break;
        }
    }

    /// Stops running steps, e.g. because the story has been re-rendered with new args. The
    /// debugger controls can carry on from the next step.
    public void stop() {
        generation++;
        cancelTimer();
        if (running) {
            running = false;
            report();
        }
    }

    private void restart(final int target, final boolean render) {
        generation++;
        cancelTimer();
        if (render) {
            rerender.run();
        }
        // Show the steps still to run, as described before they run
        entries.forEach(entry -> entry.reset(entry.step.describe(root)));
        nextStep = 0;
        targetStep = target;
        failed = false;
        running = true;
        report();
        runNext(generation);
    }

    private void runNext(final int runGeneration) {
        if (runGeneration != generation) {
            return;
        }
        if (failed || nextStep >= targetStep) {
            running = false;
            report();
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
        timer = new Timer() {
            @Override
            public void run() {
                timer = null;
                if (runGeneration == generation) {
                    task.run();
                }
            }
        };
        timer.schedule(delayMillis);
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

        if (step.getKind() == Kind.ACTION) {
            boolean success;
            try {
                step.run(root);
                entry.text = step.describe(root);
                entry.status = Status.DONE;
                report();
                success = true;
            } catch (final RuntimeException | AssertionError e) {
                fail(entry, e);
                success = false;
            }
            callback.done(success);
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
                entry.status = Status.DONE;
                report();
                callback.done(true);
            });
        } else {
            waitFor(step, entry, Duration.currentTimeMillis(), runGeneration, callback);
        }
    }

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
        execute(children.get(index), runGeneration, success -> {
            if (success) {
                executeChildren(children, index + 1, runGeneration, callback);
            } else {
                callback.done(false);
            }
        });
    }

    /// Runs the steps in the group (including any nested groups) synchronously, retrying until
    /// they all pass or the group's timeout runs out.
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
        } catch (final RuntimeException | AssertionError e) {
            failure = e;
        }
        if (failure == null) {
            entry.status = Status.DONE;
            report();
            callback.done(true);
        } else if (Duration.currentTimeMillis() - startTime < step.getTimeoutMillis()) {
            report();
            schedule(WAIT_FOR_INTERVAL_MILLIS, runGeneration,
                    () -> waitFor(step, entry, startTime, runGeneration, callback));
        } else {
            failActiveDescendants(step, failure);
            fail(entry, failure);
            callback.done(false);
        }
    }

    /// Marks the steps within a group that were running when it failed as failed.
    private void failActiveDescendants(final PlayStep group, final Throwable failure) {
        for (final PlayStep child : group.getChildren()) {
            final LogEntry childEntry = entriesByStep.get(child);
            if (childEntry.status == Status.ACTIVE) {
                fail(childEntry, failure);
            }
            failActiveDescendants(child, failure);
        }
    }

    private void fail(final LogEntry entry, final Throwable e) {
        entry.status = Status.ERROR;
        entry.error = e.getMessage() != null
                ? e.getMessage()
                : e.toString();
        report();
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
        listener.onUpdate(status, entries, nextStep, topLevelSteps.size());
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


    /// A step as shown in the Interactions addon.
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

        /// @return The step as code, e.g. `userEvent.click(...)`.
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


    private interface Callback {

        void done(boolean success);
    }
}
