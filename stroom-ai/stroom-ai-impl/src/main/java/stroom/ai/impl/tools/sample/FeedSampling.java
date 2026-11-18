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

package stroom.ai.impl.tools.sample;

import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/// The sampling of an existing feed to learn which kinds of event it holds, as survey_feed and set_shape_handling do
/// it: streams picked spread over the feed's lifetime, what reading a stream through its bytes replaced, the survey's
/// hint, and the keeping of a survey in a build's survey document.
public final class FeedSampling {

    /// Streams a survey call reads unless the user says otherwise.
    public static final int SURVEY_STREAMS = 10;
    /// The characters of a part's head read, for a feed's streams (any size).
    public static final int FEED_CHARS = 250_000;
    /// The characters of a sample stream read (stream_ids), whole when it is a sample's size.
    public static final int SAMPLE_CHARS = 2_000_000;
    /// The most records read from a feed's stream.
    public static final int FEED_RECORDS = 1000;
    /// The most records read from a sample stream, or a feed's last streams.
    public static final int SAMPLE_RECORDS = 10_000;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private FeedSampling() {
        // Static methods only.
    }

    /// @param ms A time in milliseconds since the epoch.
    /// @return It in ISO 8601 with milliseconds, in UTC, e.g. `2026-10-22T09:30:00.000Z`.
    public static String iso(final long ms) {
        return ISO.format(Instant.ofEpochMilli(ms));
    }

    /// @param n How many.
    /// @return 0 to n-1 in bisection order (last, first, middle, quarters...), so any prefix spans the whole range.
    public static List<Integer> spreadOrder(final int n) {
        if (n <= 0) {
            return List.of();
        }
        final List<Integer> order = new ArrayList<>();
        final Set<Integer> seen = new LinkedHashSet<>();
        for (final int i : new int[]{n - 1, 0}) {
            if (seen.add(i)) {
                order.add(i);
            }
        }
        final List<int[]> pending = new ArrayList<>();
        pending.add(new int[]{0, n - 1});
        while (!pending.isEmpty()) {
            final int[] range = pending.removeFirst();
            final int lo = range[0];
            final int hi = range[1];
            if (hi - lo < 2) {
                continue;
            }
            final int mid = (lo + hi) / 2;
            if (seen.add(mid)) {
                order.add(mid);
            }
            pending.add(new int[]{lo, mid});
            pending.add(new int[]{mid, hi});
        }
        for (int i = 0; i < n; i++) {
            if (!seen.contains(i)) {
                order.add(i);
            }
        }
        return order;
    }

    /// @return The term that leaves deleted streams out.
    public static ExpressionOperator notDeleted() {
        return ExpressionOperator.builder().op(Op.NOT)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.DELETED.getDisplayValue())
                .build();
    }

    /// Up to `count` unread streams spread over the feed's lifetime, in bisection order. A stream search takes a
    /// slice of time (seen: 28 of them, 2.4 s each, most of a 35 s survey_feed call): progress is said now and
    /// then.
    ///
    /// @param finder   Finds streams, as the user.
    /// @param terms    The feed's terms (its name and stream type).
    /// @param count    The most streams to pick.
    /// @param skip     The streams already read.
    /// @param progress Where how it is getting on is said.
    /// @return The streams picked, and the feed's time range (`oldest`, `newest`), empty when it has no streams.
    public static Spread pickSpread(final StreamFinder finder,
                                    final ExpressionOperator terms,
                                    final int count,
                                    final Set<Long> skip,
                                    final Consumer<String> progress) {
        // Deleted streams left out by Stroom, not after: seen in Ask Stroom AI, TWITTER's oldest stream was deleted,
        // the one row asked for was dropped, and the survey failed with "list index out of range".
        final ExpressionOperator live = ExpressionOperator.builder().op(Op.AND)
                .addOperator(terms)
                .addOperator(notDeleted())
                .build();
        progress.accept("Finding up to " + count + " streams spread over the feed's time range");
        final List<Meta> newest = live(finder.find(live, 1, true));
        final List<Meta> oldest = live(finder.find(live, 1, false));
        if (newest.isEmpty() || oldest.isEmpty()) {
            return new Spread(List.of(), Map.of());
        }
        final Map<String, String> span = new LinkedHashMap<>();
        span.put("oldest", iso(oldest.getFirst().getCreateMs()));
        span.put("newest", iso(newest.getFirst().getCreateMs()));
        final long start = oldest.getFirst().getCreateMs();
        final long end = newest.getFirst().getCreateMs() + 1;
        final List<Meta> picked = new ArrayList<>();
        final long width = Math.max(1, (end - start) / count);
        for (final int bucket : spreadOrder(count)) {
            final long lo = start + bucket * width;
            final long hi = bucket == count - 1
                    ? end
                    : start + (bucket + 1) * width;
            final ExpressionOperator slice = ExpressionOperator.builder().op(Op.AND)
                    .addOperator(live)
                    .addDateTerm(MetaFields.CREATE_TIME, Condition.BETWEEN, iso(lo) + "," + iso(hi))
                    .build();
            final List<Meta> rows = live(finder.find(slice, skip.size() + picked.size() + 1, bucket != 0));
            rows.stream()
                    .filter(meta -> !skip.contains(meta.getId()) && !ids(picked).contains(meta.getId()))
                    .findFirst()
                    .ifPresent(picked::add);
            progress.accept("Finding streams spread over the feed's time range: " + picked.size() + " of up to "
                            + count + " found");
        }
        if (picked.size() < count) {
            // Thin or clustered feeds: filled with any unread streams.
            for (final Meta meta : live(finder.find(live, skip.size() + count * 2, true))) {
                if (picked.size() >= count) {
                    break;
                }
                if (!skip.contains(meta.getId()) && !ids(picked).contains(meta.getId())) {
                    picked.add(meta);
                }
            }
        }
        return new Spread(picked, span);
    }

    private static List<Meta> live(final List<Meta> metas) {
        return metas.stream().filter(meta -> meta.getStatus() != Status.DELETED).toList();
    }

    private static Set<Long> ids(final List<Meta> metas) {
        final Set<Long> ids = new LinkedHashSet<>();
        metas.forEach(meta -> ids.add(meta.getId()));
        return ids;
    }

    /// For a stream read through its bytes: how many its character set couldn't decode, and what to do.
    ///
    /// @param report What reading the stream replaced (see `StreamCharsets.Decoded#report()`), or null.
    /// @return `undecodable_bytes` (its `encoding`, `undecodable_bytes`, `as_windows_1252` and `advice`), or an
    /// empty map when every byte decoded.
    public static Map<String, Object> charsetEntry(final Map<String, Object> report) {
        if (report == null) {
            return Map.of();
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        for (final String key : List.of("encoding", "undecodable_bytes", "as_windows_1252", "advice")) {
            entry.put(key, report.get(key));
        }
        return SampleProfiler.map("undecodable_bytes", entry);
    }

    /// @param saturated True if the feed looks covered.
    /// @param newShapes True if the call found kinds of event not known before.
    /// @param known     True if the call was given known signatures.
    /// @return What the agent does next after a survey call.
    public static String hint(final boolean saturated, final boolean newShapes, final boolean known) {
        final String translate = "Translate every shape and check it with step_records(pipeline, locations, "
                                 + "draft_code): the feed's own records are stepped where they are.";
        final String broad = " Then run the broad check (step_sample over a few whole streams with "
                             + "records_per_stream).";
        if (known && !newShapes) {
            return "No new kinds of event in these streams." + (saturated
                    ? " The feed looks covered:" + broad.substring(5)
                    : " Read more with skip_stream_ids=every stream read so far.");
        }
        if (saturated) {
            return translate + " The last streams read added no new kinds of event, so the feed looks covered."
                   + broad;
        }
        return translate + " Then call again with skip_stream_ids=every stream read so far and known_signatures="
               + "every signature so far, until saturated.";
    }

    /// Writes a survey call into the build's survey document, creating it the first time.
    ///
    /// @param records  Where the build's survey documents are.
    /// @param build    The build, or null for none (nothing is kept).
    /// @param record   The build's survey document for the feed as read before the survey, or null.
    /// @param state    The state read from it, or null.
    /// @param result   The call's result, given `survey_doc` (its `type`, `uuid`, `name`, `kinds_of_event` and
    ///                 `streams_read_in_total`).
    /// @param shapes   The call's shapes, by signature.
    /// @param examples The most examples kept a shape.
    /// @return The result.
    public static Map<String, Object> keep(final SurveyRecords records,
                                           final String build,
                                           final SurveyRecord record,
                                           final Map<String, Object> state,
                                           final Map<String, Object> result,
                                           final Map<String, Map<String, Object>> shapes,
                                           final int examples) {
        if (build == null || build.isEmpty()) {
            return result;
        }
        final String feed = (String) result.get("feed");
        SurveyRecord found = record;
        Map<String, Object> earlier = state;
        if (found == null) {
            // Made by a call just before this one, which Stroom's explorer can leave out for a moment: looked for
            // again now, and merged into, not refused as a twin at its creation (seen: two survey calls back to
            // back).
            found = records.load(build, feed).orElse(null);
            if (found != null) {
                final Map<String, Object> read = SurveyDoc.readState(found.text());
                if (read != null) {
                    earlier = read;
                }
            }
        }
        final Map<String, Object> merged = SurveyDoc.merge(earlier, result, shapes, examples);
        final SurveyRecord saved = found == null
                ? records.create(build, SurveyDoc.docName(feed), SurveyDoc.render(merged))
                : records.write(found, SurveyDoc.render(merged));
        result.put("survey_doc", SampleProfiler.map("type", "Documentation", "uuid", saved.uuid(),
                "name", saved.name(), "kinds_of_event", ((Map<?, ?>) merged.get("shapes")).size(),
                "streams_read_in_total", ((List<?>) merged.get("streams_read")).size()));
        return result;
    }


    // --------------------------------------------------------------------------------


    /// Finds streams, as the user (e.g. through `StreamReader#find`).
    @FunctionalInterface
    public interface StreamFinder {

        /// @param expression  The search.
        /// @param limit       The most streams.
        /// @param newestFirst True for the newest first, false for the oldest.
        /// @return The streams found.
        List<Meta> find(ExpressionOperator expression, int limit, boolean newestFirst);
    }

    /// Where a build's survey documents are kept: Documentation documents, one a feed, named
    /// [SurveyDoc#docName(String)].
    public interface SurveyRecords {

        /// @param build The build.
        /// @param feed  The feed.
        /// @return The build's survey document for the feed, if there is one.
        Optional<SurveyRecord> load(String build, String feed);

        /// @param build The build.
        /// @param name  The document's name.
        /// @param text  Its text.
        /// @return The document, created in the build.
        SurveyRecord create(String build, String name, String text);

        /// @param record A survey document.
        /// @param text   Its new text.
        /// @return The document, written.
        SurveyRecord write(SurveyRecord record, String text);
    }

    /// A survey document.
    ///
    /// @param uuid Its UUID.
    /// @param name Its name.
    /// @param text Its text (the survey record's Markdown).
    public record SurveyRecord(String uuid, String name, String text) {

    }

    /// Streams picked spread over a feed's lifetime.
    ///
    /// @param picked The streams, in bisection order.
    /// @param span   The feed's time range: `oldest` and `newest`, or empty when it has no streams.
    public record Spread(List<Meta> picked, Map<String, String> span) {

    }
}
