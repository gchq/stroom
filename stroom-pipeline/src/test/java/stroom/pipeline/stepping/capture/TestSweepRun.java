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

package stroom.pipeline.stepping.capture;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared run harness's terminal-state discipline - the one place that decides whether a failed or
 * terminated capture can masquerade as a complete one. Every test here pins a way a producer can end;
 * the resolver trusts these signals absolutely (a "complete" truncated stream is navigated past
 * silently), so each is load-bearing.
 */
class TestSweepRun {

    private static final ElementFingerprints FINGERPRINTS =
            new ElementFingerprints(Map.of("e1", "fp1"), Map.of("e1", "fp1"));

    private final SteppingController controller = Mockito.mock(SteppingController.class);
    private final TaskContext taskContext = Mockito.mock(TaskContext.class);
    private final SweepRun sweepRun = new SweepRun(
            new MockSecurityContext(), new CurrentUserHolder(), new ErrorReceiverProxy(), controller);

    private void execute(final StreamSweep sweep, final Runnable body, final Throwable pendingFailure) {
        sweepRun.execute(taskContext, PipelineStepRequest.builder().build(), sweep, FINGERPRINTS,
                List.of(), "Stepping capture", null, receiver -> {
                }, body, () -> pendingFailure);
    }

    @Test
    void testACleanRunIsMarkedSuccessfullyCaptured() {
        final StreamSweep sweep = new StreamSweep(1L, null);
        execute(sweep, () -> {
        }, null);
        assertThat(sweep.isSuccessfullyCaptured()).isTrue();
    }

    @Test
    void testARecordedBodyFailureMarksTheSweepErroredNotComplete() {
        // Both drivers record body failures and CARRY ON to the epilogue; if the epilogue preferred
        // markFullyCaptured, a truncated capture would look complete and readers would navigate silently
        // past its end into the next stream.
        final StreamSweep sweep = new StreamSweep(1L, null);
        execute(sweep, () -> {
        }, new RuntimeException("body failed"));
        assertThat(sweep.hasEnded()).as("the sweep still signalled").isTrue();
        assertThat(sweep.getError()).hasMessage("body failed");
        assertThat(sweep.isSuccessfullyCaptured()).isFalse();
    }

    @Test
    void testATerminateRequestedBeforeTheStartMeansTheBodyNeverRuns() {
        // The context is published before the flag is read; the session writes the flag before reading the
        // context - whichever runs second sees the other's write, so a sweep cannot start after its session
        // has closed.
        final StreamSweep sweep = new StreamSweep(1L, null);
        sweep.requestTerminate();
        final AtomicBoolean bodyRan = new AtomicBoolean();
        execute(sweep, () -> bodyRan.set(true), null);
        assertThat(bodyRan).isFalse();
        assertThat(sweep.getError()).hasMessageContaining("terminated before it started");
    }

    @Test
    void testAnErrorFromTheBodyStillSignalsTheSweepAndPropagates() {
        // Throwable, not RuntimeException: an Error (OOM is plausible on a large stream) must not leave the
        // sweep neither complete nor errored - readers would hang on it until their deadline, forever for
        // an unbounded await.
        final StreamSweep sweep = new StreamSweep(1L, null);
        final Error error = new Error("boom");
        assertThatThrownBy(() -> execute(sweep, () -> {
            throw error;
        }, null)).isSameAs(error);
        assertThat(sweep.hasEnded()).isTrue();
        assertThat(sweep.getError()).isSameAs(error);
    }

    @Test
    void testPinsAreReleasedAfterTheRun(@TempDir final Path dir) {
        // The harness pins the run's fingerprint versions and must release them however the run ends - a
        // leaked pin would make those versions unevictable forever (the retention limit gives way to pins).
        final StepDataStore store = new StepDataStore(
                dir, new SteppingConfig().withMaxRetainedFingerprintsPerElement(1));
        final ElementId e1 = new ElementId("e1");
        final StreamSweep sweep = new StreamSweep(1L, store);
        sweepRun.execute(taskContext, PipelineStepRequest.builder().build(), sweep, FINGERPRINTS,
                List.of(store), "Stepping capture", null, receiver -> {
                }, () -> {
                    store.putElementData(new StepLocation(1L, 0, 0), e1, "fp1", data());
                    // MID-RUN: a concurrent edit's write would evict fp1 (limit 1) were it not pinned by
                    // the harness - this is the half an after-the-run assert cannot see.
                    store.putElementData(new StepLocation(1L, 0, 0), e1, "fp-edit", data());
                    assertThat(store.hasElement(e1, "fp1"))
                            .as("the run's pin protected its version against a mid-run write")
                            .isTrue();
                }, () -> null);
        assertThat(store.hasElement(e1, "fp1")).isTrue();

        // With the pin released, fp1 is ordinary history and the next write retires it (limit 1).
        store.putElementData(new StepLocation(1L, 0, 0), e1, "fp2", data());
        assertThat(store.hasElement(e1, "fp1")).as("the run's pin was released").isFalse();
    }

    private CapturedElementData data() {
        return new CapturedElementData(null, CapturedData.text("out"), false, false, true, null);
    }
}
