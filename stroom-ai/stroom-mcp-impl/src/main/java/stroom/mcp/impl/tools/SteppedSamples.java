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
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/// The records the documentation's Field mapping is written from, stepped through the pipeline: the sample streams'
/// first records, and the build's survey examples beyond them, so each kind of event the survey found has its own
/// values (asked for by the user: seen in chat 173, a NetScaler stream of 10,000 records documented from its first
/// 200, its rarer kinds' fields all "(not in the sample)").
///
/// What was stepped is kept for the session, so the documentation's later rounds (a call a round of field
/// descriptions) don't step the same records again (seen there: some 20 rounds, each stepping the same 200 records).
/// Kept by everything the steps depend on: the pipeline and every document it runs, by version, the code stepped in
/// place of an element's own, the streams, the element and the survey's records; a run the call's time cut short is
/// not kept.
@Singleton
class SteppedSamples {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SteppedSamples.class);

    /// The most survey examples stepped besides the streams' first records.
    static final int MAX_SURVEY_RECORDS = 60;
    /// How long a session's stepped records are kept.
    static final Duration TTL = Duration.ofHours(4);
    // The most steppings kept for a session, and the most sessions kept: the oldest forgotten first.
    private static final int PER_SESSION = 4;
    private static final int MAX_SESSIONS = 200;

    private final PipelineStepping stepping;
    private final WriteGuard guard;
    private final Provider<DocumentAccess> documentAccessProvider;
    private final LongSupplier clock;
    private final Map<String, Map<String, Kept>> kept = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Map<String, Kept>> eldest) {
                    return size() > MAX_SESSIONS;
                }
            });

    /// @param stepping               Steps records through a pipeline.
    /// @param guard                  Lists a build's documents.
    /// @param documentAccessProvider Reads documents, as the user.
    @Inject
    SteppedSamples(final PipelineStepping stepping,
                   final WriteGuard guard,
                   final Provider<DocumentAccess> documentAccessProvider) {
        this(stepping, guard, documentAccessProvider, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param stepping               Steps records through a pipeline.
    /// @param guard                  Lists a build's documents.
    /// @param documentAccessProvider Reads documents, as the user.
    /// @param clock                  The time now, in milliseconds.
    SteppedSamples(final PipelineStepping stepping,
                   final WriteGuard guard,
                   final Provider<DocumentAccess> documentAccessProvider,
                   final LongSupplier clock) {
        this.stepping = stepping;
        this.guard = guard;
        this.documentAccessProvider = documentAccessProvider;
        this.clock = clock;
    }

    /// @param context   The call's context.
    /// @param pipeline  The pipeline.
    /// @param streamIds The sample streams.
    /// @param element   The element whose output and input are read.
    /// @param code      Code to step in place of an element's own, by element, or null.
    /// @param cap       The most of the streams' first records stepped.
    /// @param build     The build whose survey examples are stepped too, or null for none.
    /// @return The records' outputs and inputs by record key, and whether the call's time ran out first: as kept
    /// earlier in the session where nothing they depend on has changed.
    PipelineStepping.Outputs stepped(final ToolCallContext context,
                                     final SteppedPipeline pipeline,
                                     final List<Long> streamIds,
                                     final String element,
                                     final Map<String, String> code,
                                     final int cap,
                                     final String build) {
        final List<StepLocation> surveyed = surveyLocations(build);
        final String key = key(pipeline, streamIds, element, code, cap, surveyed);
        final String session = context.session().id();
        final Kept earlier = key == null
                ? null
                : NullSafe.map(kept.get(session)).get(key);
        if (earlier != null && clock.getAsLong() - earlier.atMs() < TTL.toMillis()) {
            LOGGER.debug("Stepped records for {} reused", pipeline.name());
            return earlier.outputs();
        }
        final PipelineStepping.Outputs first = stepping.outputs(context, pipeline, streamIds, element, code, cap);
        PipelineStepping.Outputs all = first;
        if (!first.stopped() && !surveyed.isEmpty()) {
            final List<StepLocation> beyond = surveyed.stream()
                    .filter(location -> !first.outputs().containsKey(SteppingWalks.recordKey(location.getMetaId(),
                            location)))
                    .toList();
            final PipelineStepping.Outputs more = stepping.outputsAt(pipeline, beyond, element, code);
            all = merged(first, more);
        }
        if (key != null && !all.stopped()) {
            kept.computeIfAbsent(session, k -> Collections.synchronizedMap(new LinkedHashMap<>(4, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Kept> eldest) {
                    return size() > PER_SESSION;
                }
            })).put(key, new Kept(all, clock.getAsLong()));
        }
        return all;
    }

    // The build's survey examples' locations (survey_feed's `location`s), in stream and record order, at most
    // MAX_SURVEY_RECORDS; none without a build or a survey.
    private List<StepLocation> surveyLocations(final String build) {
        if (NullSafe.isBlankString(build)) {
            return List.of();
        }
        final Set<List<Long>> found = new LinkedHashSet<>();
        try {
            for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
                if (DocumentationDoc.TYPE.equals(doc.type()) && doc.name().endsWith(StroomBuildFacts.SURVEY)) {
                    documentAccessProvider.get().read(doc.docRef(), DocumentationDoc.class)
                            .map(read -> SurveyDoc.readState(BuildRecords.bodyText(read)))
                            .ifPresent(state -> JsonValues.mapOrEmpty(state.get("shapes")).values()
                                    .forEach(shape -> examples(shape, found)));
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No survey examples read for build {}: {}", build, e.getMessage()));
            return List.of();
        }
        final Map<List<Long>, StepLocation> sorted = new TreeMap<>((a, b) -> {
            for (int i = 0; i < a.size(); i++) {
                final int compared = Long.compare(a.get(i), b.get(i));
                if (compared != 0) {
                    return compared;
                }
            }
            return 0;
        });
        found.stream().limit(MAX_SURVEY_RECORDS).forEach(location -> sorted.put(location,
                new StepLocation(location.get(0), location.get(1), location.get(2))));
        return new ArrayList<>(sorted.values());
    }

    private static void examples(final Object shape, final Set<List<Long>> found) {
        if (!(shape instanceof final Map<?, ?> map)) {
            return;
        }
        for (final Map<String, Object> example : JsonValues.maps(map.get("examples"))) {
            if (example.get("location") instanceof final Map<?, ?> location
                && location.get("stream") instanceof final Number stream
                && location.get("record") instanceof final Number record) {
                final long part = location.get("part") instanceof final Number number
                        ? number.longValue()
                        : 0L;
                found.add(List.of(stream.longValue(), part, record.longValue()));
            }
        }
    }

    // What the steps depend on, as one key; null when a document's version can't be read (then nothing is kept).
    private String key(final SteppedPipeline pipeline,
                       final List<Long> streamIds,
                       final String element,
                       final Map<String, String> code,
                       final int cap,
                       final List<StepLocation> surveyed) {
        try {
            final List<Object> parts = new ArrayList<>();
            parts.add(pipeline.ref().getUuid());
            parts.add(version(pipeline.ref()));
            pipeline.merged().templateChain().forEach(template -> parts.add(template.getUuid() + "@"
                                                                            + version(template)));
            for (final Object value : pipeline.merged().properties().values()) {
                if (value instanceof final Map<?, ?> doc && doc.get("uuid") != null && doc.get("type") != null) {
                    final DocRef ref = new DocRef(String.valueOf(doc.get("type")), String.valueOf(doc.get("uuid")));
                    parts.add(ref.getUuid() + "@" + version(ref));
                }
            }
            parts.add(new TreeMap<>(NullSafe.map(code)));
            parts.add(List.copyOf(streamIds));
            parts.add(element);
            parts.add(cap);
            parts.add(surveyed.stream()
                    .map(location -> location.getMetaId() + ":" + location.getPartIndex() + ":"
                                     + location.getRecordIndex())
                    .toList());
            return XsltCode.sha256(String.valueOf(parts));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Stepped records for {} not kept: {}", pipeline.name(),
                    e.getMessage()));
            return null;
        }
    }

    // A document's version, which Stroom changes on every save.
    private String version(final DocRef ref) {
        return documentAccessProvider.get().read(ref)
                .filter(AbstractDoc.class::isInstance)
                .map(doc -> ((AbstractDoc) doc).getVersion())
                .orElseThrow(() -> new IllegalStateException("no version for " + ref));
    }

    private static PipelineStepping.Outputs merged(final PipelineStepping.Outputs first,
                                                   final PipelineStepping.Outputs more) {
        final Map<String, String> outputs = new LinkedHashMap<>(first.outputs());
        final Map<String, String> inputs = new LinkedHashMap<>(first.inputs());
        more.outputs().forEach(outputs::putIfAbsent);
        more.inputs().forEach(inputs::putIfAbsent);
        return new PipelineStepping.Outputs(outputs, inputs, more.stopped());
    }


    // --------------------------------------------------------------------------------


    private record Kept(PipelineStepping.Outputs outputs, long atMs) {

    }
}
