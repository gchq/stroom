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

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamSweep {

    private static final long META = 7L;
    private static final ElementId PARSER = new ElementId("parser");
    private static final ElementId XSLT = new ElementId("translationFilter");

    private StreamSweep newSweep() {
        return new StreamSweep(META, null);
    }

    private StepLocation loc(final long record) {
        return new StepLocation((long) META, 0L, (long) record);
    }

    @Test
    void testRecordCapturedBumpsVersionAndLocation() {
        final StreamSweep sweep = newSweep();
        final long v0 = sweep.getVersion();
        sweep.recordCaptured(loc(0));
        assertThat(sweep.getVersion()).isGreaterThan(v0);
        assertThat(sweep.getLastCapturedLocation()).isEqualTo(loc(0));
        assertThat(sweep.hasEnded()).isFalse();
    }





    @Test
    void testMarkErrorSetsErrorAndCompletes() {
        final StreamSweep sweep = newSweep();
        final RuntimeException boom = new RuntimeException("boom");
        sweep.markError(boom);
        assertThat(sweep.hasEnded()).isTrue();
        assertThat(sweep.getError()).isSameAs(boom);
        // A subsequent error does not overwrite the first.
        sweep.markError(new RuntimeException("second"));
        assertThat(sweep.getError()).isSameAs(boom);
        // Awaiting on a completed sweep returns immediately.
        assertThat(sweep.awaitChangeSince(sweep.getVersion(), 5_000)).isTrue();
    }

    @Test
    void testAwaitChangeSinceReportsNoProgressWhenInterrupted() throws InterruptedException {
        // An interrupted wait must not claim progress: a caller that believes a record landed goes round
        // its resolve loop again, where the still-set interrupt flag makes the next await throw at once -
        // spinning a full store re-scan until its deadline instead of bailing out.
        final StreamSweep sweep = newSweep();
        final AtomicBoolean signalled = new AtomicBoolean(true);
        final AtomicBoolean interruptFlagPreserved = new AtomicBoolean();

        final Thread waiter = new Thread(() -> {
            signalled.set(sweep.awaitChangeSince(sweep.getVersion(), 30_000));
            interruptFlagPreserved.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        // Give the waiter time to park, then interrupt it rather than letting it time out.
        sleep(100);
        waiter.interrupt();
        waiter.join(5_000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(signalled).isFalse();
        // The interrupt is still restored for the caller.
        assertThat(interruptFlagPreserved).isTrue();
    }

    @Test
    void testAwaitCompleteReportsIncompleteWhenInterrupted() throws InterruptedException {
        final StreamSweep sweep = newSweep();
        final AtomicBoolean completed = new AtomicBoolean(true);

        final Thread waiter = new Thread(() -> completed.set(sweep.awaitEnd(30_000)));
        waiter.start();
        sleep(100);
        waiter.interrupt();
        waiter.join(5_000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(completed).isFalse();
    }

    @Test
    void testTerminateRequestIsVisibleToACaptureThatHasNotStarted() {
        // The session sets this flag before reading the task context; a capture task publishes its context
        // before reading the flag. This is what stops a queued sweep starting after its session closed.
        final StreamSweep sweep = newSweep();
        assertThat(sweep.isTerminateRequested()).isFalse();
        assertThat(sweep.getTaskContext()).isNull();

        sweep.requestTerminate();

        assertThat(sweep.isTerminateRequested()).isTrue();
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void copiesStartupIndicatorsOnlyForElementsThatRanTheSameCode() {
        final StreamSweep capture = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v1"));
        capture.setStartProcessIndicators(Map.of(
                PARSER, indicators(PARSER, "parser warning"),
                XSLT, indicators(XSLT, "No XSLT found with name or UUID \"Later\"")));
        // The XSLT's code (or an XSLT it imports) has changed since the capture; the parser's hasn't.
        final StreamSweep materialisation = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v2"));

        materialisation.copyStreamMetadataFrom(capture);

        assertThat(materialisation.getStartProcessIndicators()).containsOnlyKeys(PARSER);
    }

    @Test
    void indicatorsASweepRaisedItselfAreNotReplacedByCopiedOnes() {
        final StreamSweep capture = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v1"));
        capture.setStartProcessIndicators(Map.of(PARSER, indicators(PARSER, "from the capture")));
        final StreamSweep reprocess = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v1"));
        final Indicators own = indicators(PARSER, "from the reprocess");
        reprocess.addStartProcessIndicators(Map.of(PARSER, own));

        reprocess.copyStreamMetadataFrom(capture);

        assertThat(reprocess.getStartProcessIndicators()).containsExactlyEntriesOf(Map.of(PARSER, own));
    }

    @Test
    void addedIndicatorsReplaceCopiedOnesForTheSameElement() {
        final StreamSweep capture = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v1"));
        capture.setStartProcessIndicators(Map.of(XSLT, indicators(XSLT, "from the capture")));
        final StreamSweep reprocess = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v1"));
        reprocess.copyStreamMetadataFrom(capture);
        final Indicators own = indicators(XSLT, "from the reprocess");

        reprocess.addStartProcessIndicators(Map.of(XSLT, own));

        assertThat(reprocess.getStartProcessIndicators()).containsExactlyEntriesOf(Map.of(XSLT, own));
    }

    @Test
    void withoutFingerprintsEveryStartupIndicatorIsCopied() {
        final StreamSweep capture = new StreamSweep(META, null);
        capture.setStartProcessIndicators(Map.of(XSLT, indicators(XSLT, "error")));
        final StreamSweep materialisation = new StreamSweep(META, null, fingerprints("parser-v1", "xslt-v2"));

        materialisation.copyStreamMetadataFrom(capture);

        assertThat(materialisation.getStartProcessIndicators()).containsOnlyKeys(XSLT);
    }

    private static ElementFingerprints fingerprints(final String parser, final String xslt) {
        final Map<String, String> cumulative = Map.of(PARSER.getId(), parser, XSLT.getId(), xslt);
        return new ElementFingerprints(cumulative, cumulative);
    }

    private static Indicators indicators(final ElementId elementId, final String message) {
        final Indicators indicators = new Indicators();
        indicators.add(new StoredError(Severity.FATAL_ERROR, null, elementId, message));
        return indicators;
    }
}
