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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamCharsets;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.RawText;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/// Sample streams' raw text, read in place of text sent by the client, so a sample passes through the model once
/// (upload_sample) however many tools then read it.
class SampleStreamTexts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleStreamTexts.class);

    /// What is said to a user without the View Data permission.
    static final String NO_VIEW_DATA = "You don't have permission to view data, so the sample streams can't be "
                                       + "read.";

    /// The most parts of a stream read, as survey_feed reads at most.
    static final int MAX_PARTS = 20;
    /// The fewest characters a part is read to, however many parts share the stream's.
    static final int MIN_PART_CHARS = 100_000;

    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;

    @Inject
    SampleStreamTexts(final StreamReader streamReader, final DocumentAccess documentAccess) {
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
    }

    /// Every part of each stream is read, as its own text: a stream of several files received at once has a part
    /// each, and a pipeline processes them all. Seen in testing: a feed's five latest streams uploaded again as one
    /// stream of five parts; only the first part was read, so the checks saw almost none of its kinds of event and
    /// their extracts "matched nothing", while processing read all five.
    ///
    /// @param streamIds Sample streams.
    /// @return Their text by name (`stream <id>`, or `stream <id> part <n>` from part 0 for a stream of several),
    /// and notes on what was cut short or read through its bytes.
    /// @throws ToolRefusal if the user may not view data, or a stream can't be read.
    Texts read(final List<Long> streamIds) {
        if (!documentAccess.canViewData()) {
            throw new ToolRefusal(NO_VIEW_DATA);
        }
        final Map<String, String> texts = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();
        final List<Cut> cut = new ArrayList<>();
        for (final long streamId : streamIds) {
            final int parts;
            try {
                streamReader.meta(streamId);
                // At least the one part a stream always has.
                parts = Math.max(1, streamReader.partCount(streamId));
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
            final int read = Math.min(parts, MAX_PARTS);
            if (parts > read) {
                notes.add("stream " + streamId + " has " + parts + " parts: its first " + read + " are read");
            }
            // The stream's share of characters, spread over its parts so none is crowded out by the first.
            final int perPart = Math.max(MIN_PART_CHARS, StreamReader.MAX_SAMPLE_CHARS / read);
            for (int part = 0; part < read; part++) {
                final String name = parts == 1
                        ? "stream " + streamId
                        : "stream " + streamId + " part " + part;
                final RawText text;
                try {
                    text = streamReader.cachedRawText(streamId, part, perPart);
                } catch (final StreamReadException e) {
                    throw new ToolRefusal(e.getMessage());
                }
                texts.put(name, text.text());
                if (text.truncated()) {
                    cut.add(new Cut(name, streamId, part));
                    notes.add(name + " is read up to its first "
                              + String.format(Locale.ROOT, "%,d", text.text().length())
                              + " characters (the most a sample is read to" + (read > 1
                            ? ", shared by its parts"
                            : "") + ")");
                }
            }
            final Map<String, Object> report = streamReader.charsetReport(streamId);
            if (report != null) {
                notes.add(StreamCharsets.note(streamId, report));
            }
        }
        return new Texts(texts, notes, cut);
    }

    /// @param streamIds Streams.
    /// @return The feeds they are in, each once, in the order first met.
    /// @throws ToolRefusal if a stream can't be read.
    List<String> feedsOf(final List<Long> streamIds) {
        final Set<String> feeds = new LinkedHashSet<>();
        for (final long streamId : streamIds) {
            try {
                final String feed = streamReader.meta(streamId).getFeedName();
                if (NullSafe.isNonBlankString(feed)) {
                    feeds.add(feed);
                }
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
        }
        return List.copyOf(feeds);
    }

    /// The texts read through a build's scope over an aggregated feed: each split into its records, those of
    /// source types outside the scope left out as they are read (by the source type field and the registry's rows,
    /// the first match winning), the rest written back in the text's format. A text that holds none of the scope's
    /// records is left out; one whose format can't be read record by record is kept whole, and said so.
    ///
    /// @param texts The texts read.
    /// @param scope The build's scope.
    /// @return The scope's texts, and what was left out, said for the agent.
    /// @throws ToolRefusal if no text holds a record of the scope.
    static Scoped inScope(final Texts texts, final SourceTypeScope scope) {
        final Map<String, String> kept = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>(texts.notes());
        final Map<String, Integer> leftOut = new TreeMap<>();
        final List<String> whole = new ArrayList<>();
        int records = 0;
        for (final Map.Entry<String, String> entry : texts.texts().entrySet()) {
            final SourceTypeScope.Filtered filtered = scope.filter(entry.getValue());
            if (!filtered.filtered()) {
                whole.add(entry.getKey());
                kept.put(entry.getKey(), entry.getValue());
                continue;
            }
            filtered.passedOver().forEach((type, count) -> leftOut.merge(type, count, Integer::sum));
            records += filtered.kept();
            if (filtered.kept() > 0) {
                kept.put(entry.getKey(), filtered.text());
            }
        }
        if (kept.isEmpty()) {
            throw new ToolRefusal("None of the sample's records is in build '" + scope.build() + "''s scope ("
                                  + scope.said() + "): they are all of other source types " + leftOut.keySet()
                                  + ". Give stream_ids of streams that hold the scope's records (the survey's "
                                  + "locations say where they are).");
        }
        if (!whole.isEmpty()) {
            notes.add(String.join(", ", whole) + " can't be read record by record (XML or text): read whole, not "
                      + "through the build's scope");
        }
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("build", scope.build());
        said.put(SourceTypeScope.SOURCE_TYPES, scope.signature());
        said.put("field", scope.field());
        said.put("records_in_scope", records);
        said.put("records_left_out", leftOut);
        said.put("note", "Read through the build's scope: only records of " + scope.said() + " (by '"
                         + scope.field() + "') are in the sample; the others were left out as they were read, so "
                         + "the kinds and rules here are the scope's.");
        final List<Cut> cut = texts.cut().stream().filter(part -> kept.containsKey(part.name())).toList();
        return new Scoped(new Texts(kept, notes, cut), said);
    }

    /// @param streamId A sample stream.
    /// @return Its receipt headers (what `stroom:meta()` reads, e.g. `RemoteFile`), or none when they can't be read.
    Map<String, String> receiptHeaders(final long streamId) {
        try {
            final Object attributes = streamReader.streamAttributes(streamId).get("attributes");
            final Map<String, String> headers = new LinkedHashMap<>();
            if (attributes instanceof final Map<?, ?> map) {
                map.forEach((key, value) -> headers.put(String.valueOf(key), String.valueOf(value)));
            }
            return headers;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Stream {}'s receipt headers can't be read: {}", streamId,
                    e.getMessage()));
            return Map.of();
        }
    }


    // --------------------------------------------------------------------------------


    /// Sample streams' text.
    ///
    /// @param texts Each stream's text, by name (`stream <id>`).
    /// @param notes What was cut short, or read through its bytes.
    /// @param cut   The parts read only to the most a sample is read to.
    record Texts(Map<String, String> texts, List<String> notes, List<Cut> cut) {

        /// @param texts Each stream's text, by name.
        /// @param notes What was cut short, or read through its bytes.
        Texts(final Map<String, String> texts, final List<String> notes) {
            this(texts, notes, List.of());
        }
    }


    /// Texts read through a build's scope.
    ///
    /// @param texts The scope's texts.
    /// @param said  What was kept and left out, for the tool's result.
    record Scoped(Texts texts, Map<String, Object> said) {

    }


    /// A part read only to the most a sample is read to.
    ///
    /// @param name   Its text's name in [Texts#texts()].
    /// @param stream The stream.
    /// @param part   The part, from 0.
    record Cut(String name, long stream, int part) {

    }
}
