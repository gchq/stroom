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

package stroom.pipeline.stepping.session;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.SessionStepResolver.SessionStepResult;
import stroom.pipeline.stepping.read.StoreStepResolver;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StepDataStoreException;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSteppingSession {

    private static final ElementId E1 = new ElementId("e1");
    private static final String FP = "fp1";
    private static final ElementFingerprints FINGERPRINTS =
            new ElementFingerprints(Map.of("e1", FP), Map.of("e1", FP));

    private final SessionStepResolver resolver = new SessionStepResolver(new StoreStepResolver());

    private CapturedElementData ed(final String output) {
        return new CapturedElementData(null, CapturedData.text(output), false, false, true, null);
    }

    /**
     * A pre-populated, (optionally) completed sweep for {@code records} records (0-based) of one stream.
     */
    private StreamSweep sweptStream(final Path dir, final long metaId, final int records, final boolean complete) {
        final StepDataStore store = new StepDataStore(dir.resolve(String.valueOf(metaId)), new SteppingConfig());
        for (int r = 0; r < records; r++) {
            store.putRecord(new StepLocation(metaId, 0, r),
                    List.of(new StepDataStore.ElementRecord(E1, FP, ed("m" + metaId + "r" + r))));
        }
        final StreamSweep sweep = new StreamSweep(metaId, store);
        if (complete) {
            sweep.markFullyCaptured();
        }
        return sweep;
    }

    private SteppingSession session(final List<Long> order,
                                    final Map<Long, StreamSweep> sweeps,
                                    final AtomicInteger launchCount) {
        final SteppingSession.SweepLauncher launcher = (metaId, request, fp) -> {
            launchCount.incrementAndGet();
            return sweeps.get(metaId);
        };
        return new SteppingSession(
                "session",
                order,
                launcher,
                s -> {
                },
                sweep -> {
                },
                new SteppingConfig().getMaxSweptStreamsPerSession());
    }

    private PipelineStepRequest req(final StepType type, final StepLocation ref) {
        return PipelineStepRequest.builder().stepType(type).stepLocation(ref).build();
    }

    @Test
    void testEnsureStreamSweptIsLazyAndCached() {
        final AtomicInteger launches = new AtomicInteger();
        final StreamSweep s10 = new StreamSweep(10L, null);
        final SteppingSession session = session(List.of(10L, 20L), Map.of(10L, s10), launches);

        assertThat(session.sweepFor(10L, req(StepType.FIRST, null), FINGERPRINTS)).isSameAs(s10);
        assertThat(session.sweepFor(10L, req(StepType.FIRST, null), FINGERPRINTS)).isSameAs(s10);
        assertThat(launches.get()).isEqualTo(1);
        assertThat(session.getActiveSweeps()).containsExactly(s10);
    }

    @Test
    void testNeighbourNavigation() {
        final SteppingSession session = session(List.of(10L, 20L, 30L), Map.of(), new AtomicInteger());
        assertThat(session.firstStreamId()).hasValue(10L);
        assertThat(session.lastStreamId()).hasValue(30L);
        assertThat(session.nextStreamId(20L)).hasValue(30L);
        assertThat(session.nextStreamId(30L)).isEmpty();
        assertThat(session.prevStreamId(20L)).hasValue(10L);
        assertThat(session.prevStreamId(10L)).isEmpty();
    }

    @Test
    void testResolveFirstOnlySweepsFirstStream(@TempDir final Path dir) {
        final AtomicInteger launches = new AtomicInteger();
        final Map<Long, StreamSweep> sweeps = Map.of(
                10L, sweptStream(dir, 10L, 3, true),
                20L, sweptStream(dir, 20L, 2, true));
        final SteppingSession session = session(List.of(10L, 20L), sweeps, launches);

        final SessionStepResult result = resolver.resolve(session, req(StepType.FIRST, null), FINGERPRINTS, 5_000);
        assertThat(result.foundRecord()).isTrue();
        assertThat(result.complete()).isTrue();
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(10L, 0, 0));
        assertThat(result.stepData().getElementMap().get("e1").getOutput()).isEqualTo("m10r0");
        // Only the first stream was swept.
        assertThat(launches.get()).isEqualTo(1);
    }

    @Test
    void testForwardCrossesToNextStream(@TempDir final Path dir) {
        final AtomicInteger launches = new AtomicInteger();
        final Map<Long, StreamSweep> sweeps = Map.of(
                10L, sweptStream(dir, 10L, 3, true),
                20L, sweptStream(dir, 20L, 2, true));
        final SteppingSession session = session(List.of(10L, 20L), sweeps, launches);

        // Forward off the end of stream 10 (last record index 2) lands on the first record of stream 20.
        final SessionStepResult result = resolver.resolve(
                session, req(StepType.FORWARD, new StepLocation(10L, 0, 2)), FINGERPRINTS, 5_000);
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(20L, 0, 0));
        assertThat(launches.get()).isEqualTo(2);
    }

    @Test
    void testBackwardCrossesToPreviousStream(@TempDir final Path dir) {
        final Map<Long, StreamSweep> sweeps = Map.of(
                10L, sweptStream(dir, 10L, 3, true),
                20L, sweptStream(dir, 20L, 2, true));
        final SteppingSession session = session(List.of(10L, 20L), sweeps, new AtomicInteger());

        // Backward off the start of stream 20 lands on the last record of stream 10.
        final SessionStepResult result = resolver.resolve(
                session, req(StepType.BACKWARD, new StepLocation(20L, 0, 0)), FINGERPRINTS, 5_000);
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(10L, 0, 2));
    }

    @Test
    void testLastResolvesLastRecordOfLastStream(@TempDir final Path dir) {
        final Map<Long, StreamSweep> sweeps = Map.of(
                10L, sweptStream(dir, 10L, 3, true),
                20L, sweptStream(dir, 20L, 2, true));
        final SteppingSession session = session(List.of(10L, 20L), sweeps, new AtomicInteger());

        final SessionStepResult result = resolver.resolve(session, req(StepType.LAST, null), FINGERPRINTS, 5_000);
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(20L, 0, 1));
    }

    @Test
    void testRefreshDoesNotCrossStreams(@TempDir final Path dir) {
        final Map<Long, StreamSweep> sweeps = Map.of(10L, sweptStream(dir, 10L, 3, true));
        final SteppingSession session = session(List.of(10L, 20L), sweeps, new AtomicInteger());

        // Refresh at a record that doesn't exist in stream 10 -> not found (no crossing).
        final SessionStepResult result = resolver.resolve(
                session, req(StepType.REFRESH, new StepLocation(10L, 0, 9)), FINGERPRINTS, 5_000);
        assertThat(result.foundRecord()).isFalse();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void testIncompleteWhileStillSweeping(@TempDir final Path dir) {
        // Stream 10 has records 0,1 but is NOT complete; asking for the record after 1 must wait, then
        // return an incomplete result once the (short) timeout elapses.
        final Map<Long, StreamSweep> sweeps = Map.of(10L, sweptStream(dir, 10L, 2, false));
        final SteppingSession session = session(List.of(10L), sweeps, new AtomicInteger());

        final SessionStepResult result = resolver.resolve(
                session, req(StepType.FORWARD, new StepLocation(10L, 0, 1)), FINGERPRINTS, 150);
        assertThat(result.foundRecord()).isFalse();
        assertThat(result.complete()).isFalse();
    }

    @Test
    void testCaptureErrorReturnsErrorResult(@TempDir final Path dir) {
        final StreamSweep errored = sweptStream(dir, 10L, 1, false);
        errored.markError(new RuntimeException("capture blew up"));
        final SteppingSession session = session(List.of(10L), Map.of(10L, errored), new AtomicInteger());

        // Forward past the only record -> stream errored/complete -> error result.
        final SessionStepResult result = resolver.resolve(
                session, req(StepType.FORWARD, new StepLocation(10L, 0, 0)), FINGERPRINTS, 5_000);
        assertThat(result.complete()).isTrue();
        assertThat(result.generalError()).contains("capture blew up");
    }

    @Test
    void testCloseInvokesCallback() {
        final AtomicInteger closed = new AtomicInteger();
        final SteppingSession session = new SteppingSession(
                "s",
                List.of(1L),
                (metaId, request, fp) -> new StreamSweep(metaId, null),
                s -> closed.incrementAndGet(),
                sweep -> {
                },
                new SteppingConfig().getMaxSweptStreamsPerSession());
        session.close();
        assertThat(closed.get()).isEqualTo(1);

        // Closing is idempotent - an idle reap racing a user-driven close must not tear down twice.
        session.close();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    void testSweepCannotBeLaunchedAfterClose() {
        // A sweep launched after teardown re-creates the store dir and map entry that close() just deleted,
        // and nothing would ever clean them up again.
        final AtomicInteger launches = new AtomicInteger();
        final SteppingSession session =
                session(List.of(10L, 20L), Map.of(20L, new StreamSweep(20L, null)), launches);

        session.close();

        assertThatThrownBy(() -> session.sweepFor(20L, req(StepType.FIRST, null), FINGERPRINTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThat(launches.get()).isZero();
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void testSweptStreamsAreCappedPerSession() {
        // Without this cap a filtered step matching nothing sweeps every stream in the selection to disk.
        final AtomicInteger launches = new AtomicInteger();
        final SteppingSession session = new SteppingSession(
                "session",
                List.of(10L, 20L, 30L),
                (metaId, request, fp) -> {
                    launches.incrementAndGet();
                    return new StreamSweep(metaId, null);
                },
                s -> {
                },
                sweep -> {
                },
                2);

        session.sweepFor(10L, req(StepType.FIRST, null), FINGERPRINTS);
        session.sweepFor(20L, req(StepType.FIRST, null), FINGERPRINTS);
        // An already-swept stream is still served from the cache once the cap is reached.
        assertThat(session.sweepFor(10L, req(StepType.FIRST, null), FINGERPRINTS)).isNotNull();

        assertThatThrownBy(() -> session.sweepFor(30L, req(StepType.FIRST, null), FINGERPRINTS))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining("narrow your selection");
        assertThat(launches.get()).isEqualTo(2);
    }

    @Test
    void testForwardIgnoresAReferenceToAStreamOutsideTheSession(@TempDir final Path dir) {
        // The reference location comes from the client. Honouring a stream outside the session's (permission
        // filtered) list would sweep data the session was never scoped to; the live path resets FORWARD to
        // the first stream instead.
        final AtomicInteger launches = new AtomicInteger();
        final Map<Long, StreamSweep> sweeps = Map.of(10L, sweptStream(dir, 10L, 2, true));
        final SteppingSession session = session(List.of(10L), sweeps, launches);

        final SessionStepResult result = resolver.resolve(
                session, req(StepType.FORWARD, new StepLocation(999L, 0, 5)), FINGERPRINTS, 5_000);

        assertThat(result.foundRecord()).isTrue();
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(10L, 0, 0));
        // The out-of-session stream was never swept.
        assertThat(launches.get()).isEqualTo(1);
        assertThat(session.getActiveSweeps()).containsExactly(sweeps.get(10L));
    }

    @Test
    void testRefreshIgnoresAReferenceToAStreamOutsideTheSession(@TempDir final Path dir) {
        final SteppingSession session =
                session(List.of(10L), Map.of(10L, sweptStream(dir, 10L, 2, true)), new AtomicInteger());

        final SessionStepResult result = resolver.resolve(
                session, req(StepType.REFRESH, new StepLocation(999L, 0, 0)), FINGERPRINTS, 5_000);

        assertThat(result.foundRecord()).isFalse();
    }

    @Test
    void testRecordLandingDuringTheScanIsNotSteppedOver(@TempDir final Path dir) {
        // The resolver reads the sweep's version, scans the store, then asks whether the sweep is complete.
        // A record committed inside that window is absent from the scan but present in a now-complete
        // stream: concluding "no match here" would cross into the next stream and leave it unreachable
        // forever. RacingSweep reproduces that interleaving deterministically.
        final StepDataStore store10 = new StepDataStore(dir.resolve("10"), new SteppingConfig());
        for (int r = 0; r <= 4; r++) {
            store10.putRecord(new StepLocation(10L, 0, r),
                    List.of(new StepDataStore.ElementRecord(E1, FP, ed("m10r" + r))));
        }

        final RacingSweep s10 = new RacingSweep(10L, store10, sweep -> {
            // Commit record 5 and finish the stream, exactly between the scan and the completeness check.
            final StepLocation loc = new StepLocation(10L, 0, 5);
            store10.putRecord(loc, List.of(new StepDataStore.ElementRecord(E1, FP, ed("m10r5"))));
            sweep.recordCaptured(loc);
            sweep.markFullyCaptured();
        });

        final Map<Long, StreamSweep> sweeps = Map.of(10L, s10, 20L, sweptStream(dir, 20L, 1, true));
        final SteppingSession session = session(List.of(10L, 20L), sweeps, new AtomicInteger());

        final SessionStepResult result = resolver.resolve(
                session, req(StepType.FORWARD, new StepLocation(10L, 0, 4)), FINGERPRINTS, 5_000);

        assertThat(s10.fired).isTrue();
        assertThat(result.foundRecord()).isTrue();
        // Record 5 of stream 10, NOT record 0 of stream 20.
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(10L, 0, 5));
    }

    @Test
    void testBackwardFromARecordTheSweepHasNotReachedWaitsRatherThanLanding(@TempDir final Path dir) {
        // A step's reference location can be ahead of what this session has captured - a fresh session after
        // an idle reap, say, still stepping from where the user was. Stepping back from it must not walk
        // down over the not-yet-captured records, find each one absent, take that for "no match" and land on
        // the first record of the part. It must wait for the sweep instead.
        final StepDataStore store = new StepDataStore(dir.resolve("10"), new SteppingConfig());
        store.putRecord(new StepLocation(10L, 0, 0),
                List.of(new StepDataStore.ElementRecord(E1, FP, ed("m10r0"))));
        // Record 0 is captured; the sweep is still running and has not reached records 1-9.
        final StreamSweep s10 = new StreamSweep(10L, store);
        final SteppingSession session = session(List.of(10L), Map.of(10L, s10), new AtomicInteger());

        final SessionStepResult result = resolver.resolve(
                session, req(StepType.BACKWARD, new StepLocation(10L, 0, 9)), FINGERPRINTS, 200);

        assertThat(result.foundRecord()).isFalse();
        assertThat(result.complete()).isFalse();
        assertThat(result.foundLocation()).isNull();
    }

    @Test
    void testBackwardStepsToThePreviousRecordOnceCaptured(@TempDir final Path dir) {
        final StreamSweep s10 = sweptStream(dir, 10L, 10, true);
        final SteppingSession session = session(List.of(10L), Map.of(10L, s10), new AtomicInteger());

        final SessionStepResult result = resolver.resolve(
                session, req(StepType.BACKWARD, new StepLocation(10L, 0, 9)), FINGERPRINTS, 5_000);

        assertThat(result.foundRecord()).isTrue();
        assertThat(result.foundLocation()).isEqualTo(new StepLocation(10L, 0, 8));
    }

    // --------------------------------------------------------------------------------

    /**
     * A sweep that commits a record the first time it is asked whether it is complete, reproducing a record
     * landing in the window between the resolver's scan and its completeness check.
     */
    private static final class RacingSweep extends StreamSweep {

        private final Consumer<RacingSweep> onFirstIsComplete;
        private boolean fired;

        private RacingSweep(final long metaId,
                            final StepDataStore store,
                            final Consumer<RacingSweep> onFirstIsComplete) {
            super(metaId, store);
            this.onFirstIsComplete = onFirstIsComplete;
        }

        @Override
        public boolean isFullyCaptured() {
            if (!fired) {
                fired = true;
                onFirstIsComplete.accept(this);
            }
            return super.isFullyCaptured();
        }
    }
}
