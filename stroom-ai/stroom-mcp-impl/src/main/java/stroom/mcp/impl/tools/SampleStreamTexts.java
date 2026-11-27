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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSamplingConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.sample.PartSampler;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.streams.StreamCharsets;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.ai.impl.tools.streams.StreamReader.StreamReadException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Sample streams' raw text, read in place of text sent by the client, so a sample passes through the model once
/// (upload_sample) however many tools then read it.
class SampleStreamTexts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleStreamTexts.class);

    /// What is said to a user without the View Data permission.
    static final String NO_VIEW_DATA = "You don't have permission to view data, so the sample streams can't be "
                                       + "read.";

    // A text's name: its stream, and its part where the stream has several.
    private static final Pattern TEXT_NAME = Pattern.compile("stream (\\d+)(?: part (\\d+))?");

    /// The most parts of a stream read, as survey_feed reads at most.
    static final int MAX_PARTS = 20;

    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    SampleStreamTexts(final StreamReader streamReader,
                      final DocumentAccess documentAccess,
                      final Provider<AiConfig> aiConfigProvider) {
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// Each stream's parts are read, each as its own text: a stream of several files received at once has a part
    /// each, and a pipeline processes them all. Seen in testing: a feed's five latest streams uploaded again as one
    /// stream of five parts; only the first part was read, so the checks saw almost none of its kinds of event and
    /// their extracts "matched nothing", while processing read all five. A stream of more parts than are read has
    /// them chosen spread through it, from its first to its last (seen in user testing: an OpenWebUI audit stream
    /// of 787 parts was read through its first 20 only). Each part is read as a stream of whole events
    /// ([StreamReader#samplePart]), up to its share of the stream's characters (ai.sampling): an event too long to
    /// sample is left out and said, never cut.
    ///
    /// @param streamIds Sample streams.
    /// @return Their text by name (`stream <id>`, or `stream <id> part <n>` for a stream of several, `n` the part's
    /// own number from 0), and notes on what was left out, read only in part, or read through its bytes.
    /// @throws ToolRefusal if the user may not view data, a stream can't be read, or every event read is too long
    /// to sample.
    Texts read(final List<Long> streamIds) {
        if (!documentAccess.canViewData()) {
            throw new ToolRefusal(NO_VIEW_DATA);
        }
        final AiSamplingConfig sampling = sampling();
        final Map<String, String> texts = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();
        final List<Cut> cut = new ArrayList<>();
        final List<Skipped> skipped = new ArrayList<>();
        for (final long streamId : streamIds) {
            final int parts;
            try {
                streamReader.meta(streamId);
                // At least the one part a stream always has.
                parts = Math.max(1, streamReader.partCount(streamId));
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
            final List<Integer> chosen = PartSampler.spread(parts, MAX_PARTS);
            if (parts > chosen.size()) {
                notes.add("stream " + streamId + " has " + parts + " parts: " + chosen.size() + " of them are "
                          + "read, spread through it (parts " + PartSampler.said(chosen) + ")");
            }
            readParts(streamId, parts, chosen, sampling, new Read(texts, notes, cut, skipped));
            final Map<String, Object> report = streamReader.charsetReport(streamId);
            if (report != null) {
                notes.add(StreamCharsets.note(streamId, report));
            }
        }
        if (texts.isEmpty() && !skipped.isEmpty()) {
            throw new ToolRefusal("No event of the sample could be sampled: " + String.join("; ", notes) + ". "
                                  + "Upload a sample of shorter events, or have ai.sampling.maxEventChars raised.");
        }
        return new Texts(texts, notes, cut, skipped);
    }

    // The parts chosen of one stream, each up to its share of what is left of the stream's characters, and every one
    // at least an event of the most length: the stream's characters bound only how deep each is read.
    private void readParts(final long streamId,
                           final int parts,
                           final List<Integer> chosen,
                           final AiSamplingConfig sampling,
                           final Read read) {
        long left = sampling.getMaxCharsPerStream();
        final List<Integer> inPart = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            final int part = chosen.get(i);
            final String name = parts == 1
                    ? "stream " + streamId
                    : "stream " + streamId + " part " + part;
            final int share = PartSampler.share(left, chosen.size() - i, sampling.getMaxEventChars());
            final PartSampler.Sample sample;
            try {
                sample = streamReader.samplePart(streamId, part, new PartSampler.Limits(sampling.getMaxEventChars(),
                        share));
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
            left -= sample.text().length();
            sample.said(sampling.getMaxEventChars()).forEach(said -> read.notes().add(name + ": " + said));
            if (sample.skipped().count() > 0) {
                read.skipped().add(new Skipped(name, sample.skipped()));
            }
            if (sample.timedOut()) {
                read.notes().add(name + ": the call's time ran out while it was read, so its sample is what was "
                                 + "read by then");
            }
            if (!sample.whole()) {
                read.cut().add(new Cut(name, streamId, part));
                if (sample.skipped().count() == 0 && !sample.unterminated() && !sample.timedOut()
                    && sample.fellBack() == null) {
                    inPart.add(part);
                }
            }
            if ((sample.mode() == PartSampler.Mode.JSON_ARRAY || sample.mode() == PartSampler.Mode.XML)
                && sample.events() == 0 && !sample.whole()) {
                read.notes().add(name + ": no event of it is in the sample");
                continue;
            }
            read.texts().put(name, sample.text());
        }
        if (!inPart.isEmpty()) {
            read.notes().add((parts == 1
                    ? "stream " + streamId + " is"
                    : "stream " + streamId + " part" + (inPart.size() == 1
                            ? " "
                            : "s ") + PartSampler.said(inPart) + (inPart.size() == 1
                            ? " is"
                            : " are")) + " sampled in part: whole events up to "
                             + (inPart.size() == 1 && parts == 1
                                     ? "the "
                                     : "each part's share of the ")
                             + thousands(sampling.getMaxCharsPerStream()) + " characters of a stream's sample");
        }
    }

    private AiSamplingConfig sampling() {
        return NullSafe.getOrElseGet(aiConfigProvider.get(), AiConfig::getSampling, AiSamplingConfig::new);
    }

    private static String thousands(final long number) {
        return String.format(Locale.ROOT, "%,d", number);
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
        return inScope(texts, scope, name -> Map.of());
    }

    /// As [#inScope(Texts, SourceTypeScope)], where the scope's source type field may be a part's meta attribute
    /// ([SourceTypeScope#byPart()]): each text, a part of a stream, is kept or left out whole by its part's value,
    /// whatever its format.
    ///
    /// @param texts The texts read ([#read]).
    /// @param scope The build's scope.
    /// @return The scope's texts, and what was left out, said for the agent.
    /// @throws ToolRefusal if no text holds a record of the scope.
    Scoped scoped(final Texts texts, final SourceTypeScope scope) {
        if (!scope.byPart()) {
            return inScope(texts, scope);
        }
        final PartAttributes attributes = new PartAttributes(streamReader);
        return inScope(texts, scope, name -> partOf(name)
                .map(part -> attributes.fields(scope.field(), part.stream(), part.part()))
                .orElse(Map.of()));
    }

    /// @param name A text's name, as [#read] gives it: `stream <id>`, or `stream <id> part <n>`.
    /// @return The stream and part it is of; empty for a name not given so.
    static Optional<Cut> partOf(final String name) {
        final Matcher found = TEXT_NAME.matcher(name == null
                ? ""
                : name);
        if (!found.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Cut(name, Long.parseLong(found.group(1)), found.group(2) == null
                ? 0
                : Integer.parseInt(found.group(2))));
    }

    // The texts read through the scope, each part's fields laid over its records (a source type field read from a
    // part's meta), by the text's name.
    private static Scoped inScope(final Texts texts,
                                  final SourceTypeScope scope,
                                  final Function<String, Map<String, Object>> partFields) {
        final Map<String, String> kept = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>(texts.notes());
        final Map<String, Integer> leftOut = new TreeMap<>();
        final List<String> whole = new ArrayList<>();
        int records = 0;
        for (final Map.Entry<String, String> entry : texts.texts().entrySet()) {
            final SourceTypeScope.Filtered filtered = scope.filter(entry.getValue(), partFields.apply(entry.getKey()));
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
                         + scope.field() + "') are in the sample; the others were left out as they were read"
                         + (scope.byPart()
                ? " (each part kept or left out whole, by its stream meta attribute)"
                : "") + ", so the event types and rules here are the scope's.");
        final List<Cut> cut = texts.cut().stream().filter(part -> kept.containsKey(part.name())).toList();
        return new Scoped(new Texts(kept, notes, cut, texts.skipped()), said);
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
    /// @param texts   Each stream's text, by name (`stream <id>`).
    /// @param notes   What was left out or read only in part, or read through its bytes.
    /// @param cut     The parts not read whole (read only to the most a sample holds, or events left out).
    /// @param skipped The events left out as too long to sample, by the name of the part they are in.
    record Texts(Map<String, String> texts, List<String> notes, List<Cut> cut, List<Skipped> skipped) {

        /// @param texts Each stream's text, by name.
        /// @param notes What was cut short, or read through its bytes.
        Texts(final Map<String, String> texts, final List<String> notes) {
            this(texts, notes, List.of(), List.of());
        }

        /// @param texts Each stream's text, by name.
        /// @param notes What was cut short, or read through its bytes.
        /// @param cut   The parts not read whole.
        Texts(final Map<String, String> texts, final List<String> notes, final List<Cut> cut) {
            this(texts, notes, cut, List.of());
        }
    }


    /// Events left out of a part's sample as too long to sample.
    ///
    /// @param name   The part's text's name, as in [Texts#texts()].
    /// @param events What was left out.
    record Skipped(String name, PartSampler.SkippedEvents events) {

    }


    // What one call's reading gathers.
    private record Read(Map<String, String> texts, List<String> notes, List<Cut> cut, List<Skipped> skipped) {

    }


    /// Texts read through a build's scope.
    ///
    /// @param texts The scope's texts.
    /// @param said  What was kept and left out, for the tool's result.
    record Scoped(Texts texts, Map<String, Object> said) {

    }


    /// A part not read whole: read only to the most a sample holds, or with events left out.
    ///
    /// @param name   Its text's name in [Texts#texts()].
    /// @param stream The stream.
    /// @param part   The part, from 0.
    record Cut(String name, long stream, int part) {

    }
}
