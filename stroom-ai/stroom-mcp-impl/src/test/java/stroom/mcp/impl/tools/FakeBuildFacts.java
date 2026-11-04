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

import stroom.ai.impl.tools.KeptMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// What a build holds, as the plan's tests set it.
final class FakeBuildFacts implements BuildFacts {

    List<Map<String, Object>> docs = new ArrayList<>();
    List<String> sources = List.of();
    Map<String, String> stages = new LinkedHashMap<>();
    Map<String, String> parsers = new LinkedHashMap<>();
    Map<String, KeptMapping.Kept> mappings = new LinkedHashMap<>();
    boolean stepped;
    boolean verified;
    boolean validated;
    List<Long> ownRaw = List.of();
    // Raw Reference sample streams of the build's own feed.
    List<Long> ownRawReference = List.of();
    String kind;
    // Each feed's Reference streams, by its name.
    Map<String, List<Long>> referenceStreams = new LinkedHashMap<>();
    // Each pipeline's referenced feeds, by its UUID.
    Map<String, List<String>> referencedFeeds = new LinkedHashMap<>();
    List<Long> events = List.of();
    Map<String, Object> survey;
    List<String> checks = List.of();
    String keptXslt;
    List<Map<String, Object>> filters = List.of();
    Map<String, Map<String, Object>> destinations = new LinkedHashMap<>();
    String agreementProblem;
    Map<String, String> names = new LinkedHashMap<>();
    List<Map<String, Object>> translating = List.of();
    final List<String> columns = List.of();
    Function<String, Map<String, Object>> profiler = text -> profileOf("json lines");
    RuntimeException docsFail;

    /// @return A build's document, as the plan lists them.
    static Map<String, Object> doc(final String type, final String uuid, final String name, final Object... extra) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", type);
        doc.put("uuid", uuid);
        doc.put("name", name);
        doc.put("path", "b");
        doc.put("working_copy_of", null);
        doc.put("version_of", null);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            doc.put(String.valueOf(extra[i]), extra[i + 1]);
        }
        return doc;
    }

    /// @return A profile of the format, as the profiler gives one.
    static Map<String, Object> profileOf(final String format) {
        final Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("format", format);
        profile.put("suggested_parser", BuildPlan.PARSER_FOR_FORMAT.getOrDefault(format, List.of("DSParser"))
                .getFirst());
        return profile;
    }

    static KeptMapping.Kept kept(final String kind, final Map<String, Object> payload) {
        return new KeptMapping.Kept(kind, payload);
    }

    @Override
    public List<Map<String, Object>> buildDocs(final String build) {
        if (docsFail != null) {
            throw docsFail;
        }
        return docs;
    }

    @Override
    public Map<String, List<Map<String, Object>>> sampleStreams(final String build,
                                                               final List<Map<String, Object>> buildDocs) {
        final Map<String, List<Map<String, Object>>> streams = new LinkedHashMap<>();
        if (!ownRaw.isEmpty()) {
            streams.put("Raw Events", ownRaw.stream().map(id -> {
                final Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("id", id);
                meta.put("feed", "F");
                meta.put("createMs", id);
                return meta;
            }).toList());
        }
        if (!ownRawReference.isEmpty()) {
            streams.put("Raw Reference", ownRawReference.stream().map(id -> {
                final Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("id", id);
                meta.put("feed", "REF");
                meta.put("createMs", id);
                return meta;
            }).toList());
        }
        return streams;
    }

    @Override
    public String streamHead(final long streamId) {
        return "head of " + streamId;
    }

    @Override
    public Map<String, Object> profile(final String text) {
        return profiler.apply(text);
    }

    @Override
    public Optional<KeptMapping.Kept> xsltMapping(final String xsltUuid) {
        return Optional.ofNullable(mappings.get(xsltUuid));
    }

    @Override
    public PipelineShapes.Shape pipelineShape(final String pipelineUuid) {
        final String stage = stages.get(pipelineUuid);
        if (stage == null) {
            throw new PipelineUnreadableException("no such pipeline " + pipelineUuid);
        }
        return new PipelineShapes.Shape(stage, null, List.of(), parsers.get(pipelineUuid), List.of(), List.of(),
                List.of(), Map.of());
    }

    @Override
    public List<String> openSlots(final String pipelineUuid) {
        return List.of();
    }

    @Override
    public boolean steppedClean(final Map<String, Object> pipeline) {
        return stepped;
    }

    @Override
    public boolean verified(final Map<String, Object> pipeline) {
        return verified;
    }

    @Override
    public boolean validated(final Map<String, Object> pipeline) {
        return validated;
    }

    @Override
    public Optional<String> docName(final String type, final String uuid) {
        return Optional.of(names.getOrDefault(uuid, uuid));
    }

    @Override
    public List<Long> eventsStreams(final Map<String, Object> feed) {
        return events;
    }

    @Override
    public Optional<Map<String, Object>> elasticDestination(final String pipelineUuid) {
        return Optional.ofNullable(destinations.get(pipelineUuid));
    }

    @Override
    public Optional<String> agreementProblem(final String pipelineUuid, final Map<String, Object> destination) {
        return Optional.ofNullable(agreementProblem);
    }

    @Override
    public List<String> buildChecks(final List<Map<String, Object>> buildDocs) {
        return checks;
    }

    @Override
    public List<String> sourceFeeds(final String build, final List<Map<String, Object>> buildDocs) {
        return sources;
    }

    @Override
    public Optional<Map<String, Object>> surveyState(final List<Map<String, Object>> surveys) {
        return Optional.ofNullable(survey);
    }

    @Override
    public List<Map<String, Object>> processingFilters(final String pipelineUuid) {
        return filters;
    }

    @Override
    public List<String> suggestedColumns(final String pipelineUuid) {
        return columns;
    }

    @Override
    public Optional<String> keptXsltUuid(final String pipelineUuid) {
        return Optional.ofNullable(keptXslt);
    }

    @Override
    public List<Map<String, Object>> translating(final List<String> feeds) {
        return translating;
    }

    @Override
    public Optional<String> kind(final String build) {
        return Optional.ofNullable(kind);
    }

    @Override
    public List<Long> referenceStreams(final String feed) {
        return referenceStreams.getOrDefault(feed, List.of());
    }

    @Override
    public List<String> referencedFeeds(final String pipelineUuid) {
        return referencedFeeds.getOrDefault(pipelineUuid, List.of());
    }
}
