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

import stroom.data.shared.StreamTypeNames;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Each feed of a build of several, as the survey and build summary give it (decided by the user, 2026-11-25): its
/// raw streams, their size, and when its first and last were created ([FeedVolumes]), and whether it is dormant. A
/// dormant feed (no stream for [#DORMANT_AFTER], by its own dates, never by its name) is included like any other:
/// its history is processed too once its filter runs, so its volume is stated.
class FeedOverviews {

    /// How long a feed goes without a new stream before it is said to be dormant.
    static final Duration DORMANT_AFTER = Duration.ofDays(30);

    private final FeedVolumes volumes;

    /// @param volumes Counts each feed's streams.
    @Inject
    FeedOverviews(final FeedVolumes volumes) {
        this.volumes = volumes;
    }

    /// @param feeds The feeds.
    /// @return Each feed's overview ([#overview]), in order.
    List<Map<String, Object>> overviews(final List<String> feeds) {
        final long now = System.currentTimeMillis();
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String feed : feeds) {
            out.add(overview(volumes.volume(feed, StreamTypeNames.RAW_EVENTS), now));
        }
        return out;
    }

    /// @param volume What a feed holds of its raw streams.
    /// @param nowMs  Now.
    /// @return The feed's `feed`, `streams`, `size` (where known), `first_stream` and `last_stream` (ISO), `dormant`
    /// and `said` (it in words, with why it is dormant).
    static Map<String, Object> overview(final FeedVolumes.Volume volume, final long nowMs) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("feed", volume.feed());
        if (volume.unread()) {
            out.put("said", volume.said());
            return out;
        }
        out.put("streams", volume.streams());
        if (volume.bytes() != null) {
            out.put("size", (volume.estimated()
                    ? "about "
                    : "") + ModelStringUtil.formatIECByteSizeString(volume.bytes(), true));
        }
        if (volume.firstMs() != null) {
            out.put("first_stream", StreamProcessing.iso(volume.firstMs()));
        }
        if (volume.lastMs() != null) {
            out.put("last_stream", StreamProcessing.iso(volume.lastMs()));
        }
        final boolean dormant = dormant(volume, nowMs);
        out.put("dormant", dormant);
        out.put("said", volume.said() + (dormant
                ? "; dormant: no new stream for " + Duration.ofMillis(nowMs - volume.lastMs()).toDays() + " days. "
                  + "Included like the others: its streams are processed too once its filter runs"
                : ""));
        return out;
    }

    /// @param volume What a feed holds.
    /// @param nowMs  Now.
    /// @return Whether its last stream is older than [#DORMANT_AFTER]; false for a feed with none, or unread.
    static boolean dormant(final FeedVolumes.Volume volume, final long nowMs) {
        return !volume.unread() && volume.lastMs() != null
               && nowMs - volume.lastMs() > DORMANT_AFTER.toMillis();
    }
}
