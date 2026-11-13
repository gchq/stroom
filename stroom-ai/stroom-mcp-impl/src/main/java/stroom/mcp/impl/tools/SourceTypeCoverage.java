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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.generation.SeenSourceTypes;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.SourceTypeProfiles;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/// review_coverage on an aggregated feed, or on a feed translated whole whose survey confirmed a source type field:
/// the kinds of event processing missed, judged against what the pipeline translates. Each kind's first example
/// record is read and its source type told by the source type field and the registry's rows (the first match
/// winning).
///
/// * A source type group's pipeline (its XSLT's kept mapping a group's) is judged against the group's source types:
///   a kind of a source type another group translates (the registry's `translated` rows of another group) is left
///   to that group's pipeline (`other_groups`), never missed; one of any other source type outside the group is
///   passed over. The group's own line a document of records passed over is summed over the Error streams read.
/// * A whole translation is judged against the build's scope, where it has one; a kind of a source type a group
///   translates is left to that group's pipeline as above. Its lines of values of the source type field its survey
///   didn't see are reported (`unseen_source_types`), a prompt to survey the feed again: nothing is written for them.
///
/// A value no row matches, met in a kind, is a `new` source type where the feed has a registry, added to the build's
/// registry as a `new` row (through a working copy when the registry is beside the feed) for the user to place. Group
/// pipelines never add one from processing: values outside a group are counted with the rest passed over. Outside a
/// build nothing is written: only a build changes the registry.
class SourceTypeCoverage {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceTypeCoverage.class);

    /// The most kinds whose example record is read to tell its source type.
    static final int MAX_KINDS = 60;
    /// Who decides a row review_coverage adds.
    static final String BY = "review_coverage";
    // More records than any raw stream's text read holds: the text is split whole.
    private static final int SPLIT_ALL = 10_000_000;

    private final WriteGuard guard;
    private final TranslationBuilds builds;
    private final SourceTypeRegistries registries;
    private final StreamReader streamReader;
    private final SampleStreamTexts sampleStreamTexts;

    @Inject
    SourceTypeCoverage(final WriteGuard guard,
                       final TranslationBuilds builds,
                       final SourceTypeRegistries registries,
                       final StreamReader streamReader,
                       final SampleStreamTexts sampleStreamTexts) {
        this.guard = guard;
        this.builds = builds;
        this.registries = registries;
        this.streamReader = streamReader;
        this.sampleStreamTexts = sampleStreamTexts;
    }

    /// Judges the kinds found against what the pipeline translates, and adds the source types met that no row
    /// matches to the build's registry as `new`.
    ///
    /// @param session  The session, for the audit log of the registry's writing.
    /// @param pipeline The events pipeline reviewed.
    /// @param mapping  The mapping kept with the pipeline's XSLT (a source type group's, or a whole translation's),
    ///                 or null when it keeps none.
    /// @param groups   The kinds found (each `examples` with `raw_stream` and `record`), changed in place: each
    ///                 told's `source_type` set.
    /// @param lines    What the pipeline's XSLT logged of the source types in the Error streams read.
    /// @return The judgement; empty for a feed with no source type field (or one that can't be told).
    Optional<Judged> judge(final ToolSession session,
                           final DocRef pipeline,
                           final TranslationMapping mapping,
                           final List<Map<String, Object>> groups,
                           final CoverageReads.SourceTypeLines lines) {
        final String build = buildOf(pipeline);
        final Map<String, Object> survey = sourceTypesSurvey(build);
        final SourceTypeModule module = mapping != null && mapping.module() != null && mapping.module().isGroup()
                ? mapping.module()
                : null;
        final SeenSourceTypes seen = mapping == null || module != null
                ? null
                : mapping.seenSourceTypes();
        final String feed = survey != null
                ? JsonValues.text(survey.get("feed"))
                : feedOf(groups, lines);
        if (feed == null || feed.isBlank()) {
            return Optional.empty();
        }
        final SourceTypeRegistries.Found registry = registry(build, feed);
        final Map<String, Object> sources = survey == null
                ? Map.of()
                : JsonValues.mapOrEmpty(survey.get(SourceTypeProfiles.STATE_KEY));
        final String field = firstSet(registry == null
                        ? null
                        : JsonValues.text(registry.state().get("field")),
                JsonValues.text(sources.get(SourceTypeProfiles.FIELD)),
                module == null
                        ? null
                        : module.field(),
                seen == null
                        ? null
                        : seen.field(),
                lines.field());
        if (field == null) {
            return Optional.empty();
        }
        final List<SourceTypeRows.Row> rows = rowsOf(registry, module);
        final SourceTypeScope scope = scopeOf(build, field, rows, module, sources);
        final Map<String, String> others = otherGroups(registry, module);
        final List<Map<String, Object>> missed = new ArrayList<>();
        final List<Map<String, Object>> passedOver = new ArrayList<>();
        final List<Map<String, Object>> otherGroups = new ArrayList<>();
        final Map<String, Integer> passedRecords = new TreeMap<>();
        final Set<String> newValues = new LinkedHashSet<>();
        final SourceTypeScope reader = new SourceTypeScope(build, field, rows, null);
        final Map<Long, RecordSplitter.Chunk> read = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            final Map<String, Object> group = groups.get(i);
            final Map<String, Object> parsed = i < MAX_KINDS
                    ? exampleRecord(group, read)
                    : null;
            if (parsed == null) {
                missed.add(group);
                continue;
            }
            final String value = reader.value(parsed);
            final Optional<String> type = reader.sourceType(parsed);
            if (value != null && type.isEmpty() && registry != null) {
                newValues.add(value);
            }
            final String said = type.orElse(value == null
                    ? SourceTypeScope.UNMATCHED
                    : value + (registry == null
                            ? ""
                            : " (new)"));
            group.put("source_type", said);
            if (type.isPresent() && others.containsKey(type.get())) {
                // Another group's pipeline translates it: neither missed here nor parked.
                group.put("translated_by", others.get(type.get()));
                otherGroups.add(group);
            } else if (scope != null && !scope.inScope(parsed)) {
                // Outside what the pipeline translates (parked, dropped or new): not its to take.
                passedOver.add(group);
                passedRecords.merge(said, records(group), Integer::sum);
            } else {
                missed.add(group);
            }
        }
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("field", field);
        if (module != null) {
            said.put("group", module.name());
            said.put("group_source_types", module.sourceTypes());
        }
        if (scope != null) {
            if (!sources.isEmpty()) {
                said.put("scope", scope.share(sources));
            }
            said.put("passed_over", passedRecords);
        }
        if (!otherGroups.isEmpty()) {
            final Map<String, Object> byType = new TreeMap<>();
            otherGroups.forEach(group -> byType.put(String.valueOf(group.get("source_type")),
                    group.get("translated_by")));
            said.put("other_groups", byType);
        }
        if (module != null || lines.passedOver() > 0) {
            said.put("processing", processing(lines));
        }
        if (!lines.unseen().isEmpty()) {
            said.put("unseen_source_types", unseen(lines));
        }
        if (!newValues.isEmpty()) {
            said.put("new_source_types", List.copyOf(newValues));
            said.put("registry", addNew(session, build, feed, registry, newValues));
        }
        return Optional.of(new Judged(missed, passedOver, otherGroups, scope, module == null
                ? null
                : module.name(), said));
    }

    // The first of these that is set; null for none.
    private static String firstSet(final String... candidates) {
        for (final String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    // The registry's rows, in order; a group's own snapshot of them where the feed's registry can't be read.
    private static List<SourceTypeRows.Row> rowsOf(final SourceTypeRegistries.Found registry,
                                                   final SourceTypeModule module) {
        if (registry != null) {
            return SourceTypeRegistry.rows(registry.state());
        }
        return module == null
                ? List.of()
                : module.rows().stream().map(row -> new SourceTypeRows.Row(row.name(), row.match(), row.value()))
                        .toList();
    }

    // What the pipeline is judged against: a group's source types, else the build's scope; null for neither.
    private static SourceTypeScope scopeOf(final String build,
                                           final String field,
                                           final List<SourceTypeRows.Row> rows,
                                           final SourceTypeModule module,
                                           final Map<String, Object> sources) {
        if (module != null) {
            return new SourceTypeScope(build, field, rows, module.sourceTypes());
        }
        return build == null
                ? null
                : SourceTypeScope.of(sources, build, rows).orElse(null);
    }

    // The source types another group's pipeline translates, by the group: the registry's translated rows of a group
    // other than this pipeline's.
    private static Map<String, String> otherGroups(final SourceTypeRegistries.Found registry,
                                                   final SourceTypeModule module) {
        final Map<String, String> out = new TreeMap<>();
        if (registry == null) {
            return out;
        }
        for (final Map<String, Object> row : JsonValues.maps(registry.state().get("rows"))) {
            final String group = JsonValues.text(row.get("group"));
            if (SourceTypeRegistry.TRANSLATED.equals(row.get("status")) && !group.isBlank()
                && (module == null || !group.equals(module.name()))) {
                final String pipeline = JsonValues.text(JsonValues.mapOrEmpty(row.get("pipeline")).get("name"));
                out.put(JsonValues.text(row.get("name")), "group '" + group + "'" + (pipeline.isBlank()
                        ? ""
                        : ", pipeline '" + pipeline + "'"));
            }
        }
        return out;
    }

    // What a group's pipeline logged in processing: the records it passed over (any other source type's, or none's),
    // summed over the documents and streams read, and the raw streams it said so for.
    private static Map<String, Object> processing(final CoverageReads.SourceTypeLines lines) {
        final Map<String, Object> passed = new LinkedHashMap<>();
        passed.put("records", lines.passedOver());
        passed.put("raw_streams", lines.passedOverStreams().size());
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed_over", passed);
        return out;
    }

    // Each value a whole translation logged as not seen in its survey, with raw streams it was met in.
    private static Map<String, Object> unseen(final CoverageReads.SourceTypeLines lines) {
        final Map<String, Object> out = new LinkedHashMap<>();
        lines.unseen().forEach((value, streams) -> out.put(value, Map.of("raw_streams", List.copyOf(streams))));
        return out;
    }

    // The build a pipeline is in; null outside one.
    private String buildOf(final DocRef pipeline) {
        try {
            return guard.buildOf(pipeline).orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No build told for {}: {}", pipeline, e.getMessage()));
            return null;
        }
    }

    // The build's survey state that keeps a source type field; null when none does.
    private Map<String, Object> sourceTypesSurvey(final String build) {
        if (build == null) {
            return null;
        }
        return builds.surveyStatesInBuild(build).stream()
                .filter(state -> JsonValues.mapOrEmpty(state.get(SourceTypeProfiles.STATE_KEY))
                                         .get(SourceTypeProfiles.FIELD) != null)
                .findFirst()
                .orElse(null);
    }

    // The feed the kinds' raw streams (else the raw streams the source type lines were logged for) are in; null when
    // none can be read.
    private String feedOf(final List<Map<String, Object>> groups, final CoverageReads.SourceTypeLines lines) {
        final Set<Long> raws = new LinkedHashSet<>();
        for (final Map<String, Object> group : groups) {
            for (final Map<String, Object> example : JsonValues.maps(group.get("examples"))) {
                if (example.get("raw_stream") instanceof final Number raw) {
                    raws.add(raw.longValue());
                }
            }
        }
        raws.addAll(lines.rawStreams());
        for (final Long raw : raws) {
            try {
                return streamReader.meta(raw).getFeedName();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Stream {} not read: {}", raw, e.getMessage()));
            }
        }
        return null;
    }

    private SourceTypeRegistries.Found registry(final String build, final String feed) {
        try {
            return registries.registry(build, feed).orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No registry read for {}: {}", feed, e.getMessage()));
            return null;
        }
    }

    // A kind's first example record, parsed; null when it can't be read (or isn't structured).
    private Map<String, Object> exampleRecord(final Map<String, Object> group,
                                              final Map<Long, RecordSplitter.Chunk> read) {
        final List<Map<String, Object>> examples = JsonValues.maps(group.get("examples"));
        if (examples.isEmpty() || !(examples.getFirst().get("raw_stream") instanceof final Number raw)
            || !(examples.getFirst().get("record") instanceof final Number record)) {
            return null;
        }
        final RecordSplitter.Chunk chunk = read.computeIfAbsent(raw.longValue(), this::records);
        return chunk == null
                ? null
                : chunk.parsedAt(record.intValue());
    }

    // A raw stream's records, split as the survey splits them; null when it can't be read.
    private RecordSplitter.Chunk records(final long raw) {
        try {
            final String text = sampleStreamTexts.read(List.of(raw)).texts().values().stream().findFirst()
                    .orElse("");
            return RecordSplitter.split(text, SPLIT_ALL, true);
        } catch (final ToolRefusal | StreamReader.StreamReadException e) {
            return null;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Raw stream {} not split: {}", raw, e.getMessage()));
            return null;
        }
    }

    // The source types met that no row matches, added to the build's registry as `new` rows for the user to place;
    // said, not written, outside a build or without a registry.
    private String addNew(final ToolSession session,
                          final String build,
                          final String feed,
                          final SourceTypeRegistries.Found registry,
                          final Set<String> values) {
        if (build == null) {
            return "not written: only a build changes the registry. Onboard them in a build on the feed (its source "
                   + "types step adds them as new).";
        }
        if (registry == null) {
            return "not written: the feed has no registry yet (record_source_types build='" + build + "' writes it).";
        }
        final Map<String, Object> state = new LinkedHashMap<>(registry.state());
        final List<Map<String, Object>> rows = new ArrayList<>(JsonValues.maps(state.get("rows")));
        final Set<String> names = new LinkedHashSet<>();
        rows.forEach(row -> names.add(JsonValues.text(row.get("name"))));
        final String now = SurveyDoc.at(Instant.now());
        for (final String value : values) {
            final String name = names.contains(value)
                    ? value + " (new)"
                    : value;
            names.add(name);
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("match", SourceTypeRows.VALUE);
            row.put("value", value);
            row.put("system_name", null);
            row.put("status", SourceTypeRegistry.NEW);
            row.put("decided", decided(now));
            row.put("group", null);
            row.put("pipeline", null);
            row.put("output_feed", null);
            row.put("profile", Map.of("records_read", 0, "rests_on", "met by review_coverage in processing, not "
                                                                      + "surveyed"));
            rows.add(row);
        }
        state.put("rows", rows);
        state.put("updated", now);
        final SurveyRecord written = registries.writeRegistry(session, build, feed, registry,
                SourceTypeRegistry.render(state));
        return "added to the registry '" + written.name() + "' (" + written.uuid() + ") as new"
               + (registry.promoted()
                ? ", in the build's working copy of the one beside the feed (written back at promotion)"
                : "") + ": the user places each (record_source_types)";
    }

    private static Map<String, Object> decided(final String now) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("by", BY);
        out.put("at", now);
        out.put("reason", "met in processing after the registry was made");
        return out;
    }

    // A kind's records, or its streams for one logged once a stream.
    private static int records(final Map<String, Object> group) {
        final Object records = group.get("records");
        if (records instanceof final Number number) {
            return number.intValue();
        }
        return group.get("streams") instanceof final Number number
                ? number.intValue()
                : 0;
    }


    // --------------------------------------------------------------------------------


    /// The kinds found, judged against what the pipeline translates.
    ///
    /// @param missed      The kinds of the source types it translates (and those whose source type can't be told).
    /// @param passedOver  The kinds of source types outside them (parked, dropped or new): passed over, not missed.
    /// @param otherGroups The kinds of source types another group's pipeline translates (each with `translated_by`).
    /// @param scope       What it was judged against: a group's source types or the build's scope; null for none.
    /// @param group       The source type group whose pipeline it is, or null for a whole translation.
    /// @param said        What review_coverage says of them: `field`, `group` and `group_source_types`, `scope` (its
    ///                    share of the feed), `passed_over` (the kinds' records by source type), `other_groups`
    ///                    (source type to the group translating it), `processing` (a group's `passed_over`
    ///                    records and raw streams, as its pipeline logged them), `unseen_source_types` (a whole
    ///                    translation's values its survey didn't see), `new_source_types` and `registry`.
    record Judged(List<Map<String, Object>> missed,
                  List<Map<String, Object>> passedOver,
                  List<Map<String, Object>> otherGroups,
                  SourceTypeScope scope,
                  String group,
                  Map<String, Object> said) {

    }
}
