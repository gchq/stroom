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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFilterPollBackoff {

    private static final long STEP_MS = 10_000;
    private static final long MAX_MS = 600_000;
    private static final long NOW = 1_000_000L;

    @Test
    void pollIsDueForANewFilterThatHasNeverBeenPolled() {
        final ProcessorFilterTracker tracker = tracker(null, null, null);
        assertThat(FilterPollBackoff.isPollDue(filter(null, null), tracker, config(), NOW))
                .isTrue();
    }

    @Test
    void pollIsDueWhenTheLastPollProducedTasks() {
        final ProcessorFilterTracker tracker = tracker(NOW, 5, null);
        assertThat(FilterPollBackoff.isPollDue(filter(null, null), tracker, config(), NOW))
                .isTrue();
    }

    @Test
    void pollIsNotDueUntilTheNextPollTimeIsReached() {
        final ProcessorFilterTracker tracker = tracker(NOW, 0, NOW + STEP_MS);
        final ProcessorFilter filter = filter(null, null);
        final ProcessorConfig config = config();

        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config, NOW + STEP_MS - 1)).isFalse();
        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config, NOW + STEP_MS)).isTrue();
    }

    @Test
    void trackersWithNoNextPollTimeFallBackToTheFixedWait() {
        // e.g. a tracker written before this feature existed.
        final ProcessorFilterTracker tracker = tracker(NOW, 0, null);
        final ProcessorFilter filter = filter(null, null);
        final ProcessorConfig config = config();

        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config, NOW + STEP_MS - 1)).isFalse();
        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config, NOW + STEP_MS)).isTrue();
    }

    @Test
    void theWaitGrowsByOneStepForEachNonProducingPoll() {
        final ProcessorFilter filter = filter(null, null);
        final ProcessorConfig config = config();
        final ProcessorFilterTracker tracker = tracker(null, null, null);

        long pollMs = NOW;
        for (int pollNo = 1; pollNo <= 5; pollNo++) {
            final Long nextPollMs = FilterPollBackoff.calculateNextPollMs(filter, tracker, config, pollMs, 0);
            assertThat(nextPollMs)
                    .describedAs("Poll %s", pollNo)
                    .isEqualTo(pollMs + (pollNo * STEP_MS));

            // Record the poll as the real code does, then wait as long as we have been asked to.
            tracker.setNextPollMs(nextPollMs);
            tracker.setLastPollMs(pollMs);
            tracker.setLastPollTaskCount(0);
            pollMs = nextPollMs;
        }
    }

    @Test
    void theWaitIsCappedAtTheMaximum() {
        final ProcessorFilterTracker tracker = tracker(NOW - MAX_MS, 0, NOW);
        final Long nextPollMs = FilterPollBackoff.calculateNextPollMs(
                filter(null, null), tracker, config(), NOW, 0);
        assertThat(nextPollMs).isEqualTo(NOW + MAX_MS);
    }

    @Test
    void filterLevelMaximumOverridesTheClusterWideOne() {
        final ProcessorFilterTracker tracker = tracker(NOW - MAX_MS, 0, NOW);
        final ProcessorFilter filter = filter(new SimpleDuration(30, TimeUnit.SECONDS), null);

        final Long nextPollMs = FilterPollBackoff.calculateNextPollMs(filter, tracker, config(), NOW, 0);
        assertThat(nextPollMs).isEqualTo(NOW + 30_000);
    }

    @Test
    void zeroFilterLevelMaximumMeansPollEveryTime() {
        final ProcessorFilter filter = filter(new SimpleDuration(0, TimeUnit.SECONDS), null);
        final ProcessorFilterTracker tracker = tracker(NOW, 0, NOW);

        final Long nextPollMs = FilterPollBackoff.calculateNextPollMs(filter, tracker, config(), NOW, 0);
        assertThat(nextPollMs).isEqualTo(NOW);
        tracker.setNextPollMs(nextPollMs);
        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config(), NOW)).isTrue();
    }

    @Test
    void pollThatProducesTasksClearsTheBackoff() {
        final ProcessorFilterTracker tracker = tracker(NOW - MAX_MS, 0, NOW + MAX_MS);
        assertThat(FilterPollBackoff.calculateNextPollMs(filter(null, null), tracker, config(), NOW, 1))
                .isNull();
    }

    @Test
    void backoffIsTurnedOffAltogetherWhenTheDurationIsNull() {
        final ProcessorConfig config = config();
        config.setSkipNonProducingFiltersDuration(null);
        final ProcessorFilterTracker tracker = tracker(NOW, 0, NOW + MAX_MS);

        assertThat(FilterPollBackoff.isPollDue(filter(null, null), tracker, config, NOW)).isTrue();
        assertThat(FilterPollBackoff.calculateNextPollMs(filter(null, null), tracker, config, NOW, 0))
                .isNull();
    }

    @Test
    void filterChangedSinceTheLastPollIsPolledStraightAway() {
        // Covers new, edited, re-enabled and restored filters, all of which stamp the update time.
        final ProcessorFilterTracker tracker = tracker(NOW, 0, NOW + MAX_MS);

        assertThat(FilterPollBackoff.isPollDue(filter(null, NOW - 1), tracker, config(), NOW))
                .describedAs("Updated before the last poll")
                .isFalse();
        assertThat(FilterPollBackoff.isPollDue(filter(null, NOW + 1), tracker, config(), NOW))
                .describedAs("Updated after the last poll")
                .isTrue();
    }

    @Test
    void filterIsOnlyPolledOnceForEachChange() {
        // The poll that follows a change writes a later last poll time, so the change doesn't keep
        // forcing polls after that.
        final ProcessorFilter filter = filter(null, NOW);
        final ProcessorFilterTracker tracker = tracker(NOW - 1, 0, NOW + STEP_MS);
        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config(), NOW)).isTrue();

        tracker.setLastPollMs(NOW);
        tracker.setNextPollMs(FilterPollBackoff.calculateNextPollMs(filter, tracker, config(), NOW, 0));
        assertThat(FilterPollBackoff.isPollDue(filter, tracker, config(), NOW)).isFalse();
    }

    private ProcessorConfig config() {
        final ProcessorConfig processorConfig = new ProcessorConfig();
        processorConfig.setSkipNonProducingFiltersDuration(StroomDuration.ofMillis(STEP_MS));
        processorConfig.setSkipNonProducingFiltersMaxDuration(StroomDuration.ofMillis(MAX_MS));
        return processorConfig;
    }

    private ProcessorFilter filter(final SimpleDuration maxTaskCreationDelay,
                                   final Long updateTimeMs) {
        return ProcessorFilter.builder()
                .maxTaskCreationDelay(maxTaskCreationDelay)
                .updateTimeMs(updateTimeMs)
                .build();
    }

    private ProcessorFilterTracker tracker(final Long lastPollMs,
                                           final Integer lastPollTaskCount,
                                           final Long nextPollMs) {
        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();
        tracker.setLastPollMs(lastPollMs);
        tracker.setLastPollTaskCount(lastPollTaskCount);
        tracker.setNextPollMs(nextPollMs);
        return tracker;
    }
}
