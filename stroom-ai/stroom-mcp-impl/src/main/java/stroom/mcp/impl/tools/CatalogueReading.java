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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.CatalogueSample;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.PairMatches;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.processing.TranslationInputText;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/// How [CatalogueEventTypesTool] reads the scope's records, as processing will: the sample streams, the Data
/// Splitter and the reading (the build's latest mapping's, or the one a call gives) the catalogue is checked with.
class CatalogueReading {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CatalogueReading.class);

    private static final int MAX_SAMPLE_STREAMS = 5;
    // The keys of a translation mapping that say how a record is read (not how it is translated): what the conditions
    // are evaluated over.
    static final List<String> READING_KEYS = List.of("input", "root", "record", "xml_namespace", "json_layout",
            "json_fields", "xml_fields", "pairs", "extract", "nil_values", "for_each");
    // A rule for a reading parsed as a mapping, which needs one: never evaluated.
    private static final Map<String, Object> READING_RULE = Map.of("name", "reading", "fields",
            List.of(Map.of("path", "EventDetail/TypeId", "value", "Reading")));

    // Said of records read at a translation's input, as read_with.
    private static final String READ_AT_INPUT = "the translation's input (the pre-processor's output), stepped through "
                                                + "the pipeline described in place";

    private final SampleStreamTexts sampleStreamTexts;
    private final BuildPlan plan;
    private final TranslationBuilds builds;
    // Reads a build's records at its translation's input; none in tests that make the tool themselves.
    private Provider<TranslationInputs> inputs;

    /// @param sampleStreamTexts Reads the sample streams.
    /// @param plan              The build's plan: its sample streams.
    /// @param builds            The builds: the streams their surveys read, their scopes, saved Data Splitters and
    ///                          latest mappings.
    CatalogueReading(final SampleStreamTexts sampleStreamTexts,
                     final BuildPlan plan,
                     final TranslationBuilds builds) {
        this.sampleStreamTexts = sampleStreamTexts;
        this.plan = plan;
        this.builds = builds;
    }

    /// @param inputs Reads a build's records at its translation's input (a pipeline described in place whose
    ///               translation a pre-processor feeds).
    void setTranslationInputs(final Provider<TranslationInputs> inputs) {
        this.inputs = inputs;
    }

    /// The scope's records, read as processing will: the build's sample streams (or those given, or those the
    /// catalogue was last checked against), only the scope's source types, with the build's saved Data Splitter
    /// unless one is given, and as the build's latest mapping reads them ([#readingOf]).
    ///
    /// @param calling     The call, and what it works on.
    /// @param splitterArg The call's splitter argument exactly as given, or null.
    /// @return The records.
    Records read(final CatalogueEventTypesTool.Calling calling, final String splitterArg) {
        List<Long> streams = StartOnboardingTool.streamIds(calling.call(), CatalogueEventTypesTool.STREAM_IDS_ARG);
        if (streams.isEmpty()) {
            final List<Object> kept = JsonValues.listOrEmpty(CatalogueEventTypesTool.confirming(calling.notesNow())
                    .get(CatalogueEventTypesTool.STREAMS));
            streams = kept.stream().map(id -> (long) Double.parseDouble(String.valueOf(id))).toList();
        }
        if (streams.isEmpty()) {
            streams = plan.rawSampleStreams(calling.build(), MAX_SAMPLE_STREAMS);
        }
        if (streams.isEmpty()) {
            // A build on an existing feed, with no samples of its own: the streams its survey read.
            final Set<Long> surveyed = new LinkedHashSet<>();
            builds.surveyStatesInBuild(calling.build()).forEach(state -> surveyed.addAll(SurveyDoc.streams(state)));
            streams = surveyed.stream().limit(MAX_SAMPLE_STREAMS).toList();
        }
        if (streams.isEmpty()) {
            throw new ToolRefusal("No sample streams: upload the samples first, or give stream_ids");
        }
        // A pipeline described in place whose translation a pre-processor feeds: its records read where the
        // translation reads them (decided with the user 2026-11-25), stepped; else the streams as they are.
        final Optional<TranslationInputs.At> at = inputs == null
                ? Optional.empty()
                : inputs.get().forBuild(calling.build());
        final SampleStreamTexts.Texts texts = at.isPresent()
                ? translationInputs(calling, at.get(), streams)
                : sampleStreamTexts.read(streams);
        final Optional<SourceTypeScope> scope = builds.scope(calling.build());
        final SampleStreamTexts.Scoped scoped = scope.map(found -> sampleStreamTexts.scoped(texts, found))
                .orElse(null);
        final Map<String, String> samples = scoped == null
                ? texts.texts()
                : scoped.texts().texts();
        SplitterSpec splitter = BuildTranslationXsltTool.Request.splitter(splitterArg);
        String readWith = null;
        if (splitter == null && at.isEmpty()) {
            // The build's saved Data Splitter, where it has one (seen in chat 163: called without splitter, the
            // records read with a spec inferred afresh each time).
            final Optional<Map.Entry<String, String>> saved = builds.dataSplitter(calling.build(), null);
            if (saved.isPresent()) {
                splitter = SplitterSpec.ofConverter(saved.get().getValue());
                readWith = saved.get().getKey();
            }
        }
        final List<Object> sampleTexts = List.copyOf(samples.values());
        // The pair regexes run once a text for the whole call: the draft's pairs, the check's records and those
        // listed with their held fields are each read from the same texts (seen in eval case 36 on Haiku: 60 pair
        // regexes over 1,000 records three times, some 70% of a call).
        final PairMatches matches = PairMatches.create();
        final Map<String, Object> draft = DraftMapping.draft(sampleTexts, calling.source(), null, null, null, null,
                splitter, null, matches);
        final Map<String, Object> mapping = NullSafe.map(JsonValues.map(draft.get("mapping")));
        final Optional<Map<String, Object>> reading = readingOf(calling, mapping);
        final List<Map<String, Object>> raw = DraftMapping.records(samples, splitter).records();
        final String variantField = variantField(draft, raw);
        final CatalogueSample sample = CatalogueSample.read(reading
                .map(CatalogueReading::parsedReading)
                .orElseGet(() -> reading(mapping)), sampleTexts, splitter == null
                ? inferred(draft)
                : splitter, LocalCheck.JSON_PARSER_CUT, variantField, matches);
        final Map<String, Object> given = AbstractBuiltInTool.argsMap(calling.call())
                .get(CatalogueEventTypesTool.READING_ARG) == null
                ? null
                : reading.orElse(null);
        return new Records(streams, at.isPresent()
                ? READ_AT_INPUT
                : readWith == null
                        ? null
                        : "the build's Data Splitter '" + readWith + "'", draft, sample, scoped == null
                ? null
                : scoped.said(), HeldKindRecords.unpacked(raw, reading.orElse(mapping), matches), given);
    }

    // Each stream's records at the translation's input, a text a stream ([TranslationInputs]).
    private SampleStreamTexts.Texts translationInputs(final CatalogueEventTypesTool.Calling calling,
                                                      final TranslationInputs.At at,
                                                      final List<Long> streams) {
        final Map<Long, TranslationInputText.Text> stepped = inputs.get().texts(calling.context(), at, streams,
                Math.max(1, TranslationInputs.CATALOGUE_RECORDS / Math.max(1, streams.size())));
        if (stepped.isEmpty()) {
            throw new ToolRefusal("Stepping streams " + streams + " gave nothing at the translation's input: give "
                                  + "stream_ids of the pipeline's raw streams");
        }
        final Map<String, String> texts = new LinkedHashMap<>();
        stepped.forEach((stream, text) -> texts.put("stream " + stream, text.text()));
        return new SampleStreamTexts.Texts(texts, List.of("records read at the translation's input (a "
                                                          + "pre-processor's output), stepped through the pipeline"));
    }

    // How the records are read for the check, as the build will read them: the reading given (over the draft's own),
    // else the one an earlier call gave, else the build's latest mapping's (the one last sent for its translation,
    // else its draft this session, else the one kept with its XSLT) ([#keptElseLatest]); empty for the draft's own.
    // Seen in the reference eval: fields an extraction, pairs or held JSON gave were refused as "no record has the
    // field", the records read only as a fresh draft reads them.
    private Optional<Map<String, Object>> readingOf(final CatalogueEventTypesTool.Calling calling,
                                                    final Map<String, Object> draftMapping) {
        final Object given = AbstractBuiltInTool.argsMap(calling.call()).get(CatalogueEventTypesTool.READING_ARG);
        if (given != null) {
            if (!(given instanceof final Map<?, ?> keys)) {
                throw new ToolRefusal(CatalogueEventTypesTool.READING_ARG + " is an object of a mapping's reading "
                                      + "keys (" + String.join(", ", READING_KEYS) + ")");
            }
            final List<String> others = keys.keySet().stream()
                    .map(String::valueOf)
                    .filter(key -> !READING_KEYS.contains(key))
                    .toList();
            if (!others.isEmpty()) {
                throw new ToolRefusal(CatalogueEventTypesTool.READING_ARG + " takes only how a record is read ("
                                      + String.join(", ", READING_KEYS) + "), not " + String.join(", ", others));
            }
            final Map<String, Object> reading = readingKeys(draftMapping);
            keys.forEach((key, value) -> reading.put(String.valueOf(key), value));
            try {
                parsedReading(reading);
            } catch (final IllegalArgumentException e) {
                throw new ToolRefusal(CatalogueEventTypesTool.READING_ARG + " can't be read: " + e.getMessage());
            }
            return Optional.of(reading);
        }
        return keptElseLatest(calling.build(), JsonValues.map(CatalogueEventTypesTool.confirming(calling.notesNow())
                .get(CatalogueEventTypesTool.READING)), () -> builds.latestMapping(calling.context(), calling.build()));
    }

    /// How the build's catalogue reads its records when a call gives no reading: the reading an earlier call gave
    /// (kept in the source notes' confirmation), else the build's latest mapping's reading keys; empty for the
    /// draft's own. The reading given wins: it is the one the catalogue was told to read by, where a draft made
    /// before it may still read whole records (seen in eval case 69: the catalogue told to read each item of a
    /// batch, the calls after it read whole batches as an earlier draft did). One lookup, for catalogue_event_types
    /// and the draft that follows the catalogue ([TranslationBuilds#catalogueForEach]).
    ///
    /// @param build  The build, for the log.
    /// @param kept   The reading an earlier call gave, or null.
    /// @param latest The build's latest mapping ([TranslationBuilds#latestMapping]), asked for only when no reading
    ///               was kept.
    /// @return The reading; empty when neither can be read.
    static Optional<Map<String, Object>> keptElseLatest(final String build,
                                                        final Map<String, Object> kept,
                                                        final Supplier<Optional<Map<String, Object>>> latest) {
        if (kept != null) {
            try {
                parsedReading(kept);
                return Optional.of(kept);
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(() -> LogUtil.message("The reading kept for {} can't be read: {}", build,
                        e.getMessage()));
            }
        }
        final Optional<Map<String, Object>> mapping = latest.get();
        if (mapping.isPresent()) {
            final Map<String, Object> reading = readingKeys(mapping.get());
            try {
                parsedReading(reading);
                return Optional.of(reading);
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(() -> LogUtil.message("The latest mapping of {} can't be read, so the draft's reading is "
                                                   + "used: {}", build, e.getMessage()));
            }
        }
        return Optional.empty();
    }

    // A mapping's reading keys alone.
    private static Map<String, Object> readingKeys(final Map<String, Object> mapping) {
        final Map<String, Object> reading = new LinkedHashMap<>();
        READING_KEYS.forEach(key -> {
            if (mapping.get(key) != null) {
                reading.put(key, mapping.get(key));
            }
        });
        return reading;
    }

    // A reading as the mapping the conditions are evaluated by (its pairs read as extractions, as the translation
    // reads them).
    private static TranslationMapping parsedReading(final Map<String, Object> reading) {
        final Map<String, Object> json = new LinkedHashMap<>(reading);
        json.putIfAbsent("input", "data_splitter");
        json.put("events", List.of(READING_RULE));
        return TranslationMapping.parse(json);
    }

    // The Data Splitter the draft inferred for the records (a data_splitter input given none), or null.
    @SuppressWarnings("unchecked")
    private static SplitterSpec inferred(final Map<String, Object> draft) {
        if (draft.get("splitter") instanceof final Map<?, ?> spec) {
            try {
                return SplitterSpec.fromMap((Map<String, ?>) spec);
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(() -> LogUtil.message("The draft's Data Splitter can't be read back: {}",
                        e.getMessage()));
            }
        }
        return null;
    }

    // A mapping the conditions are evaluated by: how the draft reads a record (its input, held fields, nil values
    // and extractions), its rules aside.
    private static TranslationMapping reading(final Map<String, Object> mapping) {
        final List<Extraction> extractions = new ArrayList<>();
        for (final Map<String, Object> entry : JsonValues.maps(mapping.get("extract"))) {
            try {
                extractions.add(Extraction.fromJson(entry));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("An extraction of the draft can't be read: {}", e.getMessage()));
            }
        }
        final String input = NullSafe.nonBlankStringElse(ToolText.str(mapping.getOrDefault("input", "")),
                "data_splitter");
        return CatalogueSample.reading(input, CatalogueEventTypesTool.text(mapping.get("record")),
                CatalogueEventTypesTool.text(mapping.get("xml_namespace")),
                JsonValues.strings(mapping.get("json_fields")), JsonValues.strings(mapping.get("xml_fields")),
                JsonValues.strings(mapping.get("nil_values")), extractions);
    }

    // The field a record's line format (its variant) is written in, when the records are in several; else none.
    @SuppressWarnings("unchecked")
    private static String variantField(final Map<String, Object> draft, final List<Map<String, Object>> records) {
        if (draft.get("splitter") instanceof final Map<?, ?> spec) {
            try {
                final SplitterSpec read = SplitterSpec.fromMap((Map<String, ?>) spec);
                return NullSafe.isEmptyCollection(read.parts())
                        ? null
                        : read.variantField();
            } catch (final IllegalArgumentException e) {
                // The build's saved converter, whose spec the draft can't read back: its variant field where the
                // records have it.
                return records.stream().anyMatch(record -> record.containsKey(SplitterSpec.VARIANT))
                        ? SplitterSpec.VARIANT
                        : null;
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// The scope's records, as read for a call.
    ///
    /// @param streams         The sample streams read.
    /// @param readWith        What they were read with (the build's saved Data Splitter, or the translation's input),
    ///                        said; or null.
    /// @param draft           The draft made from them (its reading and its rules, the seed's).
    /// @param sample          The records as processing reads them, for the check.
    /// @param scopeSaid       What the scope left out, or null without one.
    /// @param evidenceRecords The records as the draft reads them, the fields inside held values among them.
    /// @param given           The reading the call gave, or null.
    record Records(List<Long> streams,
                   String readWith,
                   Map<String, Object> draft,
                   CatalogueSample sample,
                   Map<String, Object> scopeSaid,
                   List<Map<String, Object>> evidenceRecords,
                   Map<String, Object> given) {

        /// What was read, said in a result.
        ///
        /// @param result The result it is said in.
        void said(final Map<String, Object> result) {
            if (readWith != null) {
                result.put("read_with", readWith);
            }
            if (scopeSaid != null) {
                result.put("scope", scopeSaid);
            }
        }
    }
}
