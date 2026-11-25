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

package stroom.mcp.impl.tools;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Each feed of a build of several as the build summary gives it ([FeedOverviews]): decided by the user (2026-11-25),
/// a dormant feed (by its own dates, never its name) is included, its volume and dates stated.
class TestFeedOverviews {

    private static final long NOW = Instant.parse("2026-11-25T00:00:00Z").toEpochMilli();

    @Test
    void aFeedWithNoStreamForOverThirtyDaysIsDormantAndSaidSo() {
        final long last = NOW - Duration.ofDays(200).toMillis();
        final FeedVolumes.Volume volume = new FeedVolumes.Volume("ACME-OLD", "Raw Events", 12, 4096L, false,
                last - Duration.ofDays(100).toMillis(), last, false);

        final Map<String, Object> overview = FeedOverviews.overview(volume, NOW);

        assertThat(overview).containsEntry("feed", "ACME-OLD").containsEntry("streams", 12L)
                .containsEntry("dormant", true).containsEntry("last_stream", "2026-05-09T00:00:00.000Z");
        assertThat(String.valueOf(overview.get("said"))).contains("12 streams", "dormant: no new stream for 200 days",
                "Included like the others");
    }

    @Test
    void aLiveFeedAnEmptyOneAndAnUnreadOneAreNotDormant() {
        final FeedVolumes.Volume live = new FeedVolumes.Volume("ACME", "Raw Events", 3, null, false, NOW - 1000,
                NOW - 10, false);
        assertThat(FeedOverviews.overview(live, NOW)).containsEntry("dormant", false).doesNotContainKey("size");
        // Exactly 30 days is not past it.
        final FeedVolumes.Volume edge = new FeedVolumes.Volume("ACME", "Raw Events", 1, 1L, false,
                NOW - FeedOverviews.DORMANT_AFTER.toMillis(), NOW - FeedOverviews.DORMANT_AFTER.toMillis(), false);
        assertThat(FeedOverviews.dormant(edge, NOW)).isFalse();
        final FeedVolumes.Volume empty = new FeedVolumes.Volume("NEW", "Raw Events", 0, 0L, false, null, null,
                false);
        assertThat(FeedOverviews.overview(empty, NOW)).containsEntry("dormant", false)
                .containsEntry("said", "NEW (Raw Events): no streams yet");
        final FeedVolumes.Volume unread = new FeedVolumes.Volume("X", "Raw Events", 0, null, false, null, null,
                true);
        assertThat(FeedOverviews.overview(unread, NOW)).doesNotContainKey("dormant")
                .containsEntry("said", "X (Raw Events): its streams couldn't be counted");
    }
}
