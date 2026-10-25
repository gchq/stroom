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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The streams the indexing tools index, and what they hold (the Stroom MCP server's `tools/indexing.py`
/// `require_events`, `_require_input`, `_is_discovery` and `_build_of_stream`, and `summarise_events`' path
/// population): an index holds the Events an events pipeline writes, never a source's own data, unless it is a
/// discovery index, which indexes raw data as it is. create_indexing_pipeline's port checks its streams with
/// [#requireEvents].
class IndexingInputs {

    /// How many events are read to see which paths a sample populates.
    static final int EVENTS_READ = 200;

    private final StreamReader streamReader;
    private final StreamSummaries streamSummaries;
    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;
    private final BuildPlan plan;

    /// @param streamReader    Reads each stream's meta.
    /// @param streamSummaries Profiles Events streams.
    /// @param pipelineShapes  Says what a pipeline does.
    /// @param documentAccess  Reads a pipeline's XSLTs, as the user.
    /// @param plan            Says which build a pipeline is in.
    @Inject
    IndexingInputs(final StreamReader streamReader,
                   final StreamSummaries streamSummaries,
                   final PipelineShapes pipelineShapes,
                   final DocumentAccess documentAccess,
                   final BuildPlan plan) {
        this.streamReader = streamReader;
        this.streamSummaries = streamSummaries;
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
        this.plan = plan;
    }

    /// Each stream is an Events stream: never a source's own data, whatever its content looks like (seen: a raw
    /// `<records>` stream taken as the Events to plan an index from, and a plan drafted from nothing).
    ///
    /// @param streamIds The streams.
    /// @throws ToolRefusal for a stream that isn't Events, or can't be read.
    void requireEvents(final List<Long> streamIds) {
        for (final long streamId : streamIds) {
            final Meta meta;
            try {
                meta = streamReader.meta(streamId);
            } catch (final RuntimeException e) {
                throw new ToolRefusal(StreamSummaries.reason(e));
            }
            final String type = meta.getTypeName();
            if ("Records".equals(type)) {
                throw new ToolRefusal("Stream " + streamId + " is a Records stream: records:2 records, not Events, so "
                                      + "an event-logging field plan doesn't fit it. Index records as they are: "
                                      + "draft_index_mapping discovery={'input': 'delimited', 'timestamp_field': ...} "
                                      + "(it reads records:2 data elements by name), on an indexing template with an "
                                      + "XMLParser.");
            }
            if (!"Events".equals(type)) {
                throw new ToolRefusal("Stream " + streamId + " is " + (type == null
                        ? "None"
                        : PyText.repr(type)) + ", not Events: the source's own data, whatever its XML or "
                                      + "records look like. An index holds the Events an events pipeline writes: "
                                      + "process the sample through it (create_processor_filter, "
                                      + "wait_for_processing) and give those Events streams (stage 1 first).");
            }
        }
    }

    /// The streams an indexing pipeline is checked over: Events, unless it is a discovery pipeline, which indexes a
    /// source's own data as it is (seen in e2e: a discovery index's template refused for its raw sample).
    ///
    /// @param pipelineUuid The indexing pipeline.
    /// @param streamIds    The streams.
    /// @param given        The plan given for it, or null.
    /// @throws ToolRefusal for streams that aren't Events, of a pipeline that isn't a discovery one.
    void requireInput(final String pipelineUuid, final List<Long> streamIds, final FieldPlan given) {
        try {
            requireEvents(streamIds);
        } catch (final ToolRefusal e) {
            if (!isDiscovery(pipelineUuid, given)) {
                throw e;
            }
        }
    }

    /// Whether the pipeline indexes raw data as it is: its plan says so (the one given, else the one kept with its
    /// XSLT), or its parser is a raw one. An XML discovery pipeline's XMLParser is also an indexing pipeline's.
    ///
    /// @param pipelineUuid The pipeline.
    /// @param given        The plan given for it, or null.
    /// @return True for a discovery pipeline.
    boolean isDiscovery(final String pipelineUuid, final FieldPlan given) {
        if (given != null) {
            return given.discovery() != null;
        }
        if ("discovery".equals(pipelineShapes.shape(pipelineUuid).stage())) {
            return true;
        }
        final MergedPipeline merged = pipelineShapes.merged(pipelineUuid);
        for (final Map.Entry<ElementProperty, Object> entry : merged.properties().entrySet()) {
            if ("xslt".equals(entry.getKey().name()) && entry.getValue() instanceof final Map<?, ?> doc
                && doc.get("uuid") != null) {
                final Optional<KeptMapping.Kept> kept = documentAccess.read(new DocRef(XsltDoc.TYPE,
                                String.valueOf(doc.get("uuid"))), XsltDoc.class)
                        .flatMap(xslt -> KeptMapping.readMapping(xslt.getDescription()));
                if (kept.isPresent() && "index".equals(kept.get().kind())
                    && kept.get().payload().get("discovery") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /// @param streamId An Events stream.
    /// @return The build whose pipeline wrote it, if any.
    Optional<String> buildOfStream(final long streamId) {
        try {
            final String pipelineUuid = streamReader.meta(streamId).getPipelineUuid();
            return NullSafe.isBlankString(pipelineUuid)
                    ? Optional.empty()
                    : plan.buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    /// @param streamIds Events streams.
    /// @return Their profile, as summarise_events gives it: `path_population`, `data_names` and the rest.
    /// @throws ToolRefusal if a stream can't be read.
    Map<String, Object> summariseEvents(final List<Long> streamIds) {
        try {
            return streamSummaries.summariseEvents(streamIds, EVENTS_READ);
        } catch (final RuntimeException e) {
            throw new ToolRefusal(StreamSummaries.reason(e));
        }
    }

    /// [#population(Map)], with each Data element's share by its Name as well (`<path>[@Name='<name>']/@Value`), so
    /// a field read from one Data entry is counted. Not in the Stroom MCP server, whose documentation said "not in
    /// the sample" for every such field (seen: SecretServer's, read from `EventDetail//Data[@Name='...']/@Value`,
    /// though the documents written from the sample held their values).
    ///
    /// @param summary A profile from [#summariseEvents].
    /// @return How often each event path, and each named Data entry, is populated (%).
    static Map<String, Double> populationWithDataNames(final Map<String, Object> summary) {
        final Map<String, Double> out = population(summary);
        if (summary.get("data_names") instanceof final Map<?, ?> byPath) {
            byPath.forEach((path, names) -> {
                if (names instanceof final Map<?, ?> shares) {
                    shares.forEach((name, share) -> out.put(path + "[@Name='" + name + "']/@Value",
                            share instanceof final Number number
                                    ? number.doubleValue()
                                    : 0.0));
                }
            });
        }
        return out;
    }

    /// @param summary A profile from [#summariseEvents].
    /// @return How often each path (and named Data entry) is populated, as a share of all events (%), by the action
    /// element of the events that have it; empty when the profile doesn't say.
    static Map<String, Map<String, Double>> populationByAction(final Map<String, Object> summary) {
        final Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        if (summary.get(StreamSummaries.POPULATION_BY_ACTION) instanceof final Map<?, ?> byAction) {
            byAction.forEach((action, paths) -> {
                final Map<String, Double> shares = new LinkedHashMap<>();
                if (paths instanceof final Map<?, ?> given) {
                    given.forEach((path, share) -> shares.put(String.valueOf(path), share instanceof final Number n
                            ? n.doubleValue()
                            : 0.0));
                }
                out.put(String.valueOf(action), shares);
            });
        }
        return out;
    }

    /// @param summary A profile from [#summariseEvents].
    /// @return How often each event path is populated (%).
    static Map<String, Double> population(final Map<String, Object> summary) {
        final Map<String, Double> out = new LinkedHashMap<>();
        if (summary.get("path_population") instanceof final Map<?, ?> population) {
            population.forEach((path, share) -> out.put(String.valueOf(path), share instanceof final Number number
                    ? number.doubleValue()
                    : 0.0));
        }
        return out;
    }
}
