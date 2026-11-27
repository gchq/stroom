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

import stroom.ai.impl.tools.streams.StreamReader;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What a check covered of each feed of a build of several (decided by the user, 2026-11-25: coverage is reported
/// per feed, so a feed missing from it is seen): each Events stream's source feed (its raw parent's feed; the
/// build's Events are on its test feed), the streams and records of each, and the build's feeds with none.
class FeedCoverage {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedCoverage.class);

    private final StreamReader streamReader;
    private final WriteGuard guard;
    private final BuildPlan plan;

    /// @param streamReader Reads each stream's meta.
    /// @param guard        Finds the build a pipeline is in.
    /// @param plan         Gives the feeds a build is for.
    @Inject
    FeedCoverage(final StreamReader streamReader, final WriteGuard guard, final BuildPlan plan) {
        this.streamReader = streamReader;
        this.guard = guard;
        this.plan = plan;
    }

    /// @param streamIds Events streams a check read.
    /// @param records   The records it read of each, where known.
    /// @return Each feed's coverage ([#coverage]), for the build of the pipeline that wrote the first stream; empty
    /// when it is in no build of several feeds.
    Optional<Map<String, Object>> ofStreams(final List<Long> streamIds, final Map<Long, Long> records) {
        if (streamIds.isEmpty()) {
            return Optional.empty();
        }
        try {
            final String pipeline = streamReader.meta(streamIds.getFirst()).getPipelineUuid();
            if (pipeline == null) {
                return Optional.empty();
            }
            return guard.buildOf(new DocRef(PipelineDoc.TYPE, pipeline))
                    .flatMap(build -> ofBuild(build, streamIds, records));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No coverage by feed for streams {}: {}", streamIds, e.getMessage()));
            return Optional.empty();
        }
    }

    /// @param build     A build.
    /// @param streamIds Events streams a check read.
    /// @param records   The records it read of each, where known.
    /// @return Each feed's coverage ([#coverage]); empty for a build of fewer than two feeds.
    Optional<Map<String, Object>> ofBuild(final String build,
                                          final List<Long> streamIds,
                                          final Map<Long, Long> records) {
        final List<String> feeds = plan.buildFeeds(build);
        if (feeds.size() < 2) {
            return Optional.empty();
        }
        final Map<Long, String> sources = new LinkedHashMap<>();
        for (final Long id : streamIds) {
            sources.put(id, sourceFeed(id));
        }
        return Optional.of(coverage(feeds, sources, records));
    }

    /// @param feeds   The feeds the build is for.
    /// @param sources Each stream checked and its source feed (null where it can't be read).
    /// @param records The records read of each stream, where known.
    /// @return `by_feed` (each feed's `streams`, and `records` where known), `missing` (the build's feeds no stream
    /// is of) and `said`.
    static Map<String, Object> coverage(final List<String> feeds,
                                        final Map<Long, String> sources,
                                        final Map<Long, Long> records) {
        final Map<String, long[]> counts = new LinkedHashMap<>();
        feeds.forEach(feed -> counts.put(feed, new long[]{0, 0}));
        sources.forEach((stream, source) -> {
            final String feed = source == null
                    ? "(not known)"
                    : feeds.stream().filter(source::equalsIgnoreCase).findFirst().orElse(source);
            final long[] count = counts.computeIfAbsent(feed, k -> new long[]{0, 0});
            count[0]++;
            count[1] += NullSafe.map(records).getOrDefault(stream, 0L);
        });
        final boolean withRecords = !NullSafe.map(records).isEmpty();
        final Map<String, Object> byFeed = new LinkedHashMap<>();
        final List<String> missing = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        counts.forEach((feed, count) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("streams", count[0]);
            if (withRecords) {
                entry.put("records", count[1]);
            }
            byFeed.put(feed, entry);
            if (count[0] == 0) {
                missing.add(feed);
            }
            said.add(feed + ": " + (count[0] == 0
                    ? "none"
                    : count[0] + " stream" + (count[0] == 1
                            ? ""
                            : "s") + (withRecords
                            ? ", " + count[1] + " records"
                            : "")));
        });
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("by_feed", byFeed);
        out.put("missing", missing);
        out.put("said", String.join("; ", said) + (missing.isEmpty()
                ? "."
                : ". Not covered: " + String.join(", ", missing) + " (no stream of " + (missing.size() == 1
                        ? "it"
                        : "them") + " among those checked: process and check one of each, or tell the user why "
                  + "not)."));
        return out;
    }

    // The feed a stream is of: its raw parent's (the build's Events are on its test feed), else its own.
    private String sourceFeed(final long streamId) {
        try {
            final Meta meta = streamReader.meta(streamId);
            if (meta.getParentMetaId() != null) {
                return streamReader.meta(meta.getParentMetaId()).getFeedName();
            }
            return meta.getFeedName();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Stream {}'s feed not read: {}", streamId, e.getMessage()));
            return null;
        }
    }
}
