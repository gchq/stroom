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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.StepLocation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamSweep {

    private static final long META = 7L;

    private StreamSweep newSweep() {
        return new StreamSweep(META, null);
    }

    private StepLocation loc(final long record) {
        return new StepLocation(META, 0, record);
    }

    @Test
    void testRecordCapturedBumpsVersionAndLocation() {
        final StreamSweep sweep = newSweep();
        final long v0 = sweep.getVersion();
        sweep.recordCaptured(loc(0));
        assertThat(sweep.getVersion()).isGreaterThan(v0);
        assertThat(sweep.getLastCapturedLocation()).isEqualTo(loc(0));
        assertThat(sweep.isComplete()).isFalse();
    }

    @Test
    void testAwaitReturnsWhenVersionAlreadyAdvanced() {
        // A record captured between reading the version and awaiting must not be missed (no lost wake-up).
        final StreamSweep sweep = newSweep();
        final long known = sweep.getVersion();
        sweep.recordCaptured(loc(0));
        assertThat(sweep.awaitChangeSince(known, 5_000)).isTrue();
    }

    @Test
    void testAwaitWakesOnRecordFromAnotherThread() throws InterruptedException {
        final StreamSweep sweep = newSweep();
        final long known = sweep.getVersion();
        final Thread producer = new Thread(() -> {
            sleep(50);
            sweep.recordCaptured(loc(0));
        });
        producer.start();
        final boolean signalled = sweep.awaitChangeSince(known, 5_000);
        producer.join();
        assertThat(signalled).isTrue();
        assertThat(sweep.getVersion()).isGreaterThan(known);
    }

    @Test
    void testAwaitWakesOnComplete() throws InterruptedException {
        final StreamSweep sweep = newSweep();
        final long known = sweep.getVersion();
        final Thread producer = new Thread(() -> {
            sleep(50);
            sweep.markComplete();
        });
        producer.start();
        assertThat(sweep.awaitChangeSince(known, 5_000)).isTrue();
        producer.join();
        assertThat(sweep.isComplete()).isTrue();
    }

    @Test
    void testAwaitTimesOut() {
        final StreamSweep sweep = newSweep();
        // Nothing happens, so the wait must return false once the timeout elapses.
        assertThat(sweep.awaitChangeSince(sweep.getVersion(), 100)).isFalse();
        assertThat(sweep.isComplete()).isFalse();
    }

    @Test
    void testMarkErrorSetsErrorAndCompletes() {
        final StreamSweep sweep = newSweep();
        final RuntimeException boom = new RuntimeException("boom");
        sweep.markError(boom);
        assertThat(sweep.isComplete()).isTrue();
        assertThat(sweep.getError()).isSameAs(boom);
        // A subsequent error does not overwrite the first.
        sweep.markError(new RuntimeException("second"));
        assertThat(sweep.getError()).isSameAs(boom);
        // Awaiting on a completed sweep returns immediately.
        assertThat(sweep.awaitChangeSince(sweep.getVersion(), 5_000)).isTrue();
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
