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

/// review_coverage on an aggregated feed: the kinds of event processing missed, judged against the build's scope
/// ([SourceTypeScope]). Each kind's first example record is read and its source type told by the source type field
/// and the registry's rows (the first match winning): a kind of a source type outside the scope is passed over, not
/// missed (the scope's translation isn't meant to take it), and a value no row matches is a `new` source type, added
/// to the build's registry as a `new` row (through a working copy when the registry is beside the feed) for the user
/// to place. Outside a build nothing is written: only a build changes the registry.
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

    /// Judges the kinds found against the build's scope, and adds the source types met that no row matches to the
    /// build's registry as `new`.
    ///
    /// @param session  The session, for the audit log of the registry's writing.
    /// @param pipeline The events pipeline reviewed.
    /// @param groups   The kinds found (each `examples` with `raw_stream` and `record`), changed in place: each
    ///                 told's `source_type` set.
    /// @return The judgement; empty for a feed with no source type field (or one that can't be told).
    Optional<Judged> judge(final ToolSession session, final DocRef pipeline, final List<Map<String, Object>> groups) {
        final String build = buildOf(pipeline);
        final Map<String, Object> survey = sourceTypesSurvey(build);
        final String feed = survey != null
                ? JsonValues.text(survey.get("feed"))
                : feedOf(groups);
        if (feed == null || feed.isBlank()) {
            return Optional.empty();
        }
        final SourceTypeRegistries.Found registry = registry(build, feed);
        final Map<String, Object> sources = survey == null
                ? Map.of()
                : JsonValues.mapOrEmpty(survey.get(SourceTypeProfiles.STATE_KEY));
        final String field = registry != null && registry.state().get("field") instanceof final String named
                ? named
                : JsonValues.text(sources.get(SourceTypeProfiles.FIELD));
        if (field == null || field.isBlank()) {
            return Optional.empty();
        }
        final List<SourceTypeRows.Row> rows = registry == null
                ? List.of()
                : SourceTypeRegistry.rows(registry.state());
        final SourceTypeScope scope = build == null
                ? null
                : SourceTypeScope.of(sources, build, rows).orElse(null);
        final SourceTypeScope reader = new SourceTypeScope(build, field, scope == null
                ? rows
                : scope.rows(), null);
        final List<Map<String, Object>> missed = new ArrayList<>();
        final List<Map<String, Object>> passedOver = new ArrayList<>();
        final Map<String, Integer> passedRecords = new TreeMap<>();
        final Set<String> newValues = new LinkedHashSet<>();
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
            if (value != null && type.isEmpty()) {
                newValues.add(value);
            }
            final String said = type.orElse(value == null
                    ? SourceTypeScope.UNMATCHED
                    : value + " (new)");
            group.put("source_type", said);
            if (scope != null && !scope.inScope(parsed)) {
                // Outside the scope (parked, dropped or new): not the scope's translation's to take.
                passedOver.add(group);
                passedRecords.merge(said, records(group), Integer::sum);
            } else {
                missed.add(group);
            }
        }
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("field", field);
        if (scope != null) {
            said.put("scope", scope.share(sources));
            said.put("passed_over", passedRecords);
        }
        if (!newValues.isEmpty()) {
            said.put("new_source_types", List.copyOf(newValues));
            said.put("registry", addNew(session, build, feed, registry, newValues));
        }
        return Optional.of(new Judged(missed, passedOver, scope, said));
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

    // The feed the kinds' raw streams are in; null when none can be read.
    private String feedOf(final List<Map<String, Object>> groups) {
        for (final Map<String, Object> group : groups) {
            for (final Map<String, Object> example : JsonValues.maps(group.get("examples"))) {
                if (example.get("raw_stream") instanceof final Number raw) {
                    try {
                        return streamReader.meta(raw.longValue()).getFeedName();
                    } catch (final RuntimeException e) {
                        LOGGER.debug(() -> LogUtil.message("Stream {} not read: {}", raw, e.getMessage()));
                    }
                }
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


    /// The kinds found, judged against the build's scope.
    ///
    /// @param missed     The kinds of the scope's source types (and those whose source type can't be told).
    /// @param passedOver The kinds of source types outside the scope: passed over, not missed.
    /// @param scope      The build's scope, or null for none.
    /// @param said       What review_coverage says of them: `field`, `scope` (its share of the feed),
    ///                   `passed_over` (records by source type), `new_source_types` and `registry`.
    record Judged(List<Map<String, Object>> missed,
                  List<Map<String, Object>> passedOver,
                  SourceTypeScope scope,
                  Map<String, Object> said) {

    }
}
