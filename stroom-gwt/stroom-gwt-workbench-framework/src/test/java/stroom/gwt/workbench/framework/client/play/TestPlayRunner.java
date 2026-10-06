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

import stroom.gwt.workbench.framework.client.play.PlayRunner.Cancellable;
import stroom.gwt.workbench.framework.client.play.PlayRunner.Control;
import stroom.gwt.workbench.framework.client.play.PlayRunner.Environment;
import stroom.gwt.workbench.framework.client.play.PlayRunner.LogEntry;
import stroom.gwt.workbench.framework.client.play.PlayRunner.RunStatus;
import stroom.gwt.workbench.framework.client.play.PlayRunner.Status;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestPlayRunner {

    @Test
    void testRunsStepsInOrder() {
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.run("a", () -> ran.add("a"));
        play.run("b", () -> ran.add("b"));

        final Harness harness = new Harness(play);
        harness.runner().start();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.PLAYING);
        harness.environment.runAll();

        assertThat(ran).containsExactly("a", "b");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.statuses()).containsOnly(Status.DONE);
        assertThat(harness.environment.delays).containsExactly(PlayRunner.STEP_DELAY_MILLIS,
                PlayRunner.STEP_DELAY_MILLIS);
    }

    @Test
    void testStepGroupChildrenArePaced() {
        // Regression: the steps in a step(...) group ran back to back, with no pause between
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.step("Group", () -> {
            play.run("a", () -> ran.add("a"));
            play.run("b", () -> ran.add("b"));
            play.step("Nested", () -> play.run("c", () -> ran.add("c")));
        });

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(ran).containsExactly("a", "b", "c");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        // The group, a, b, the nested group and c each wait before running
        assertThat(harness.environment.delays).hasSize(5).containsOnly(PlayRunner.STEP_DELAY_MILLIS);
        assertThat(harness.entries()).extracting(LogEntry::getDepth).containsExactly(0, 1, 1, 1, 2);
    }

    @Test
    void testWaitForRetriesUntilItPasses() {
        final AtomicInteger tries = new AtomicInteger();
        final Play play = new Play();
        play.waitFor(() -> play.run("check", () -> {
            if (tries.incrementAndGet() < 3) {
                throw new PlayException("Not yet");
            }
        }));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(tries.get()).isEqualTo(3);
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.environment.delays).containsExactly(PlayRunner.STEP_DELAY_MILLIS,
                PlayRunner.WAIT_FOR_INTERVAL_MILLIS, PlayRunner.WAIT_FOR_INTERVAL_MILLIS);
    }

    @Test
    void testWaitForTimesOut() {
        final AtomicInteger tries = new AtomicInteger();
        final Play play = new Play();
        play.waitFor(200, () -> play.run("check", () -> {
            tries.incrementAndGet();
            throw new PlayException("Never");
        }));
        play.run("after", () -> tries.set(-100));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(tries.get()).isBetween(4, 6);
        assertThat(harness.entries().get(0).getStatus()).isEqualTo(Status.ERROR);
        assertThat(harness.entries().get(0).getError()).isEqualTo("Never");
        assertThat(harness.entries().get(1).getStatus()).isEqualTo(Status.ERROR);
        // The step after the failure doesn't run
        assertThat(harness.entries().get(2).getStatus()).isEqualTo(Status.WAITING);
    }

    @Test
    void testSleep() {
        final Play play = new Play();
        play.sleep(300);
        // Inside a waitFor a sleep does nothing
        play.waitFor(() -> play.sleep(5000));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.environment.delays).containsExactly(PlayRunner.STEP_DELAY_MILLIS, 300,
                PlayRunner.STEP_DELAY_MILLIS);
    }

    @Test
    void testAnyThrowableFailsTheStep() {
        // Regression: only RuntimeException and AssertionError were caught, so e.g. a
        // StackOverflowError left the runner PLAYING forever
        final Play play = new Play();
        play.run("overflow", () -> {
            throw new StackOverflowError("Too deep");
        });

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getError()).isEqualTo("Too deep");
    }

    @Test
    void testAnyThrowableFailsAWaitFor() {
        final Play play = new Play();
        play.waitFor(100, () -> play.run("overflow", () -> {
            throw new StackOverflowError("Too deep");
        }));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getError()).isEqualTo("Too deep");
    }

    @Test
    void testUncaughtErrorFailsTheRunningStep() {
        // Regression: errors thrown by the story's event handlers were swallowed so the play passed
        final Play play = new Play();
        final Harness harness = new Harness(play);
        play.run("click", () -> harness.environment.reportError("java.lang.IllegalStateException: Boom"));
        play.run("after", () -> {
        });

        assertThat(harness.environment.catching).isFalse();
        harness.runner().start();
        assertThat(harness.environment.catching).isTrue();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getStatus()).isEqualTo(Status.ERROR);
        assertThat(harness.entries().get(0).getError())
                .isEqualTo("Uncaught error in the story: java.lang.IllegalStateException: Boom");
        assertThat(harness.entries().get(1).getStatus()).isEqualTo(Status.WAITING);
        // Errors are caught for the whole life of the story's renderings
        assertThat(harness.environment.catching).isTrue();
        harness.runner().dispose();
        assertThat(harness.environment.catching).isFalse();
    }

    @Test
    void testUncaughtErrorBetweenStepsFailsTheNextStep() {
        final Play play = new Play();
        final Harness harness = new Harness(play);
        // E.g. a timer the story started throws before the next step
        play.run("start timer", () -> harness.environment.schedule(5,
                () -> harness.environment.reportError("Timer failed")));
        play.run("next", () -> {
        });
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getStatus()).isEqualTo(Status.DONE);
        assertThat(harness.entries().get(1).getError()).isEqualTo("Uncaught error in the story: Timer failed");
    }

    @Test
    void testUncaughtErrorFailsAWaitForAtOnce() {
        final AtomicInteger tries = new AtomicInteger();
        final Play play = new Play();
        final Harness harness = new Harness(play);
        play.waitFor(() -> play.run("check", () -> {
            tries.incrementAndGet();
            harness.environment.reportError("Handler failed");
            throw new PlayException("Not yet");
        }));
        harness.runner().start();
        harness.environment.runAll();

        assertThat(tries.get()).isOne();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getError()).isEqualTo("Uncaught error in the story: Handler failed");
    }

    @Test
    void testFailedRenderRunsNoSteps() {
        // Regression: when re-rendering failed the steps still ran against the broken story
        final AtomicInteger runs = new AtomicInteger();
        final AtomicBoolean renders = new AtomicBoolean(true);
        final Play play = new Play();
        play.run("count", runs::incrementAndGet);
        final Harness harness = new Harness(play, renders::get);
        harness.runner().start();
        harness.environment.runAll();
        assertThat(runs.get()).isOne();

        renders.set(false);
        harness.runner().control(Control.RERUN);
        harness.environment.runAll();
        harness.runner().control(Control.END);
        harness.runner().control(Control.NEXT);
        harness.environment.runAll();

        assertThat(runs.get()).isOne();
        assertThat(harness.renders.get()).isEqualTo(1);
    }

    @Test
    void testStopAbandonsTheRunAndNextCarriesOn() {
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.run("a", () -> ran.add("a"));
        play.run("b", () -> ran.add("b"));
        play.run("c", () -> ran.add("c"));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runNext();
        assertThat(ran).containsExactly("a");

        harness.runner().stop();
        harness.environment.runAll();
        assertThat(ran).containsExactly("a");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.PAUSED);

        harness.runner().control(Control.NEXT);
        harness.environment.runAll();
        assertThat(ran).containsExactly("a", "b");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.PAUSED);

        harness.runner().control(Control.END);
        harness.environment.runAll();
        assertThat(ran).containsExactly("a", "b", "c");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void testRewindAndRerun() {
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.run("a", () -> ran.add("a"));
        play.run("b", () -> ran.add("b"));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        harness.runner().control(Control.REWIND);
        harness.environment.runAll();
        assertThat(harness.renders.get()).isOne();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.PAUSED);
        assertThat(harness.statuses()).containsOnly(Status.WAITING);

        // A rerun abandons a run in progress
        harness.runner().control(Control.RERUN);
        harness.environment.runNext();
        harness.runner().control(Control.RERUN);
        harness.environment.runAll();
        assertThat(ran).containsExactly("a", "b", "a", "a", "b");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void testStepsCantBeAddedWhileTheStepsRun() {
        // Regression: a step added inside play.run(...) was silently dropped
        final Play play = new Play();
        play.run("adds a step", () -> play.click(play.getByText("Save")));
        play.run("finds", () -> play.findByText("Saved"));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getError()).contains("A step can't be added while the steps run");
    }

    @Test
    void testFindByInsideAStepFails() {
        final Play play = new Play();
        play.run("finds", () -> play.findByText("Saved"));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getError()).contains("play.findBy");
    }

    @Test
    void testCapture() {
        final AtomicInteger counter = new AtomicInteger(5);
        final List<Object> seen = new ArrayList<>();
        final Play play = new Play();
        final Value<Integer> before = play.capture("before", counter::get);
        play.run("increment", counter::incrementAndGet);
        play.expect("difference", () -> counter.get() - before.get()).toBe(1);
        play.run("record", () -> seen.add(before.get()));

        final Harness harness = new Harness(play);
        harness.runner().start();
        assertThat(harness.entries().get(0).getText()).isEqualTo("const before = value");
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(seen).containsExactly(5);
        assertThat(harness.entries().get(0).getText()).isEqualTo("const before = 5");

        // A rerun captures afresh
        harness.runner().control(Control.RERUN);
        harness.environment.runAll();
        assertThat(seen).containsExactly(5, 6);
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void testCaptureReadTooEarlyFails() {
        final Play play = new Play();
        final List<Value<String>> captured = new ArrayList<>();
        play.run("read", () -> captured.get(0).get());
        captured.add(play.capture("later", () -> "x"));

        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries().get(0).getError())
                .isEqualTo("The captured value 'later' was read before the step capturing it ran");
    }

    @Test
    void testErrorAfterCompletionMarksTheRunErrored() {
        // Regression: errors thrown after the play completed were lost and the story showed green
        final Play play = new Play();
        play.run("a", () -> {
        });
        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);

        harness.environment.reportError("Late failure");

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries()).hasSize(2);
        assertThat(harness.entries().get(0).getStatus()).isEqualTo(Status.DONE);
        final LogEntry error = harness.entries().get(1);
        assertThat(error.getText()).isEqualTo(PlayRunner.UNHANDLED_ERROR_TEXT);
        assertThat(error.getStatus()).isEqualTo(Status.ERROR);
        assertThat(error.getDepth()).isZero();
        assertThat(error.getError()).isEqualTo("Uncaught error in the story: Late failure");

        // A rerun starts afresh
        harness.runner().control(Control.RERUN);
        harness.environment.runAll();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.entries()).hasSize(1);
    }

    @Test
    void testErrorWhilePausedMarksTheRunErrored() {
        // Regression: after a rewind, an error while re-rendering never reached the Interactions
        // addon and Next/End then completed green
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.run("a", () -> ran.add("a"));
        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        harness.runner().control(Control.REWIND);
        harness.environment.runAll();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.PAUSED);
        harness.environment.reportError("Strict mode failure");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);

        harness.runner().control(Control.END);
        harness.runner().control(Control.NEXT);
        harness.environment.runAll();
        assertThat(ran).containsExactly("a");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
    }

    @Test
    void testErrorWhileRerenderingFailsTheFirstStep() {
        final Play play = new Play();
        play.run("a", () -> {
        });
        final AtomicBoolean throwOnRender = new AtomicBoolean();
        final AtomicInteger renders = new AtomicInteger();
        final FakeEnvironment environment = new FakeEnvironment();
        final List<RunStatus> statuses = new ArrayList<>();
        final List<List<LogEntry>> reported = new ArrayList<>();
        final PlayRunner runner = new PlayRunner(play, null, () -> {
            renders.incrementAndGet();
            if (throwOnRender.get()) {
                environment.reportError("Render failure");
            }
            return true;
        }, (status, entries, nextStep, stepCount) -> {
            statuses.add(status);
            reported.add(entries);
        }, environment);
        runner.start();
        environment.runAll();

        throwOnRender.set(true);
        runner.control(Control.RERUN);
        environment.runAll();
        assertThat(statuses.get(statuses.size() - 1)).isEqualTo(RunStatus.ERRORED);
        assertThat(reported.get(reported.size() - 1).get(0).getError())
                .isEqualTo("Uncaught error in the story: Render failure");

        // On a rewind, with no step to fail, the run fails
        runner.control(Control.REWIND);
        environment.runAll();
        assertThat(statuses.get(statuses.size() - 1)).isEqualTo(RunStatus.ERRORED);
        assertThat(reported.get(reported.size() - 1)).extracting(LogEntry::getText)
                .containsExactly("a", PlayRunner.UNHANDLED_ERROR_TEXT);
        assertThat(renders.get()).isEqualTo(2);
    }

    @Test
    void testStoryWithoutStepsReportsErrors() {
        final Harness harness = new Harness(new Play());
        harness.runner().start();
        harness.environment.runAll();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.entries()).isEmpty();

        harness.runner().reportError("Clean up failed");

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
        assertThat(harness.entries()).extracting(LogEntry::getError)
                .containsExactly("Uncaught error in the story: Clean up failed");
        // Only the first error is shown
        harness.environment.reportError("Another");
        assertThat(harness.entries()).hasSize(1);
    }

    @Test
    void testErrorAfterStopMarksTheRunErrored() {
        final Play play = new Play();
        play.run("a", () -> {
        });
        play.run("b", () -> {
        });
        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runNext();
        harness.runner().stop();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.PAUSED);

        harness.environment.reportError("After new args");
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.ERRORED);
    }

    @Test
    void testErrorInTheRunnerEndsTheRunErrored() {
        // Regression: an exception in the runner's own continuation (e.g. reporting) left the run
        // stuck as PLAYING
        final Play play = new Play();
        play.run("a", () -> {
        });
        play.run("b", () -> {
        });
        final FakeEnvironment environment = new FakeEnvironment();
        final List<RunStatus> statuses = new ArrayList<>();
        final List<List<LogEntry>> reported = new ArrayList<>();
        final AtomicInteger updates = new AtomicInteger();
        final PlayRunner runner = new PlayRunner(play, null, () -> true, (status, entries, nextStep, stepCount) -> {
            if (updates.incrementAndGet() == 3) {
                throw new IllegalStateException("Listener broke");
            }
            statuses.add(status);
            reported.add(entries);
        }, environment);
        runner.start();
        environment.runAll();

        assertThat(statuses.get(statuses.size() - 1)).isEqualTo(RunStatus.ERRORED);
        final List<LogEntry> last = reported.get(reported.size() - 1);
        assertThat(last.get(last.size() - 1).getText()).isEqualTo(PlayRunner.RUNNER_ERROR_TEXT);
        assertThat(last.get(last.size() - 1).getError()).isEqualTo("Listener broke");
    }

    @Test
    void testStepWhoseDescriptionFailsIsStillShown() {
        final Play play = new Play();
        play.addStep(PlayStep.action(root -> {
            throw new IllegalStateException("No description");
        }, root -> {
        }));
        final Harness harness = new Harness(play);
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.entries().get(0).getText()).contains("No description");
    }

    @Test
    void testSpyChangesInThePlayBodyAreSteps() {
        // Regression: mockReturnValue()/mockClear() called in the play body ran once, when the
        // play function was built, so were lost on a rerun (which renders new spies)
        final Play play = new Play();
        final Spy spy = play.spy("onTestPlayRunnerSpySteps");
        spy.mockReturnValue("x");
        final List<Object> returned = new ArrayList<>();
        play.run("call", () -> returned.add(Spies.register("onTestPlayRunnerSpySteps").call()));
        spy.mockClear();
        play.expect(spy).not().toHaveBeenCalled();

        final Harness harness = new Harness(play, () -> {
            Spies.startRendering();
            Spies.register("onTestPlayRunnerSpySteps");
            return true;
        });
        Spies.startRendering();
        Spies.register("onTestPlayRunnerSpySteps");
        harness.runner().start();
        harness.environment.runAll();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.entries()).extracting(LogEntry::getText)
                .startsWith("onTestPlayRunnerSpySteps.mockReturnValue(\"x\")")
                .contains("onTestPlayRunnerSpySteps.mockClear()");

        harness.runner().control(Control.RERUN);
        harness.environment.runAll();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(returned).containsExactly("x", "x");
    }

    @Test
    void testSpyChangesInsideAStepHappenAtOnce() {
        final Play play = new Play();
        final Spy spy = play.spy("onTestPlayRunnerSpyNow");
        play.run("clear", spy::mockClear);
        final Harness harness = new Harness(play);
        Spies.startRendering();
        Spies.register("onTestPlayRunnerSpyNow").call();
        harness.runner().start();
        harness.environment.runAll();

        assertThat(harness.entries()).hasSize(1);
        assertThat(Spies.get("onTestPlayRunnerSpyNow").getCallCount()).isZero();
    }

    @Test
    void testValueExpectationReadsTheValueOnlyWhenItRuns() {
        // Regression: the value was read to describe the step, before it ran and several times
        final AtomicInteger reads = new AtomicInteger();
        final Play play = new Play();
        play.expect("count", reads::incrementAndGet).toBe(1);

        final Harness harness = new Harness(play);
        harness.runner().start();
        assertThat(reads.get()).isZero();
        assertThat(harness.entries().get(0).getText()).isEqualTo("expect(count).toBe(1)");
        harness.environment.runAll();

        assertThat(reads.get()).isOne();
        assertThat(harness.lastStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(harness.entries().get(0).getText()).isEqualTo("expect(1).toBe(1)");
    }


    // --------------------------------------------------------------------------------


    private static final class Harness {

        private final Play play;
        private final BooleanSupplier render;
        private final FakeEnvironment environment = new FakeEnvironment();
        private final List<RunStatus> runStatuses = new ArrayList<>();
        private final AtomicInteger renders = new AtomicInteger();
        private List<LogEntry> lastEntries = new ArrayList<>();
        private PlayRunner runner;

        private Harness(final Play play) {
            this(play, () -> true);
        }

        private Harness(final Play play, final BooleanSupplier render) {
            this.play = play;
            this.render = render;
        }

        /// @return The runner, created once all the play's steps have been added.
        private PlayRunner runner() {
            if (runner == null) {
                runner = new PlayRunner(play, null, () -> {
                    renders.incrementAndGet();
                    return render.getAsBoolean();
                }, (status, entries, nextStep, stepCount) -> {
                    runStatuses.add(status);
                    lastEntries = entries;
                }, environment);
            }
            return runner;
        }

        private RunStatus lastStatus() {
            return runStatuses.get(runStatuses.size() - 1);
        }

        private List<LogEntry> entries() {
            return lastEntries;
        }

        private List<Status> statuses() {
            final List<Status> statuses = new ArrayList<>();
            for (final LogEntry entry : lastEntries) {
                statuses.add(entry.getStatus());
            }
            return statuses;
        }
    }


    // --------------------------------------------------------------------------------


    /// Timers on a fake clock, run on demand.
    private static final class FakeEnvironment implements Environment {

        private final List<Task> tasks = new ArrayList<>();
        // The delays the runner asked for, in order
        private final List<Integer> delays = new ArrayList<>();
        private double time;
        private Consumer<String> onError;
        private boolean catching;

        @Override
        public double now() {
            return time;
        }

        @Override
        public Cancellable schedule(final int delayMillis, final Runnable task) {
            final Task scheduled = new Task(time + delayMillis, task);
            tasks.add(scheduled);
            delays.add(delayMillis);
            return () -> tasks.remove(scheduled);
        }

        @Override
        public void catchErrors(final Consumer<String> onError) {
            this.onError = onError;
            catching = true;
        }

        @Override
        public void stopCatchingErrors() {
            onError = null;
            catching = false;
        }

        private void reportError(final String message) {
            if (onError != null) {
                onError.accept(message);
            }
        }

        private boolean runNext() {
            if (tasks.isEmpty()) {
                return false;
            }
            Task next = tasks.get(0);
            for (final Task task : tasks) {
                if (task.at < next.at) {
                    next = task;
                }
            }
            tasks.remove(next);
            time = Math.max(time, next.at);
            next.task.run();
            return true;
        }

        private void runAll() {
            int count = 0;
            while (runNext()) {
                if (++count > 10_000) {
                    throw new IllegalStateException("Too many tasks");
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Task {

        private final double at;
        private final Runnable task;

        private Task(final double at, final Runnable task) {
            this.at = at;
            this.task = task;
        }
    }
}
