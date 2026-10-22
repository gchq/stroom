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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.validation.EventChecks;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.ai.impl.tools.validation.ValidationException;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// `check_events` - checks event XML both ways: against the event-logging XSD this Stroom holds, and against the
/// quality rules beyond the schema. Given Events streams that processing wrote, it reads their events itself, and
/// when they pass records the pipelines that wrote them as validated in their build's record (the plan's
/// `validated` step), with the plan's next step. Ported from the Stroom MCP server's `tools/validation.py`, on
/// [EventChecks] and [EventStreams].
///
/// Read as the user: the XML schemas are those the user may view, and streams are read only where the user may
/// view their feeds and has View Data.
class CheckEventsTool extends AbstractBuiltInTool {

    static final String NAME = "check_events";
    static final String EVENTS_XML_ARG = "events_xml";
    static final String SCHEMA_VERSION_ARG = "schema_version";
    static final String STREAM_IDS_ARG = "stream_ids";
    /// The most records read from the streams in all (the Stroom MCP server's max_sample_records).
    static final int MAX_RECORDS = EventLocations.MAX_RECORDS;

    private final EventChecks eventChecks;
    private final EventStreams eventStreams;
    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;
    private final BuildRecords buildRecords;
    private final BuildPlan plan;

    /// @param eventChecks    Validates events against the schemas this Stroom holds.
    /// @param eventStreams   Reads the events of streams.
    /// @param streamReader   Finds the pipeline that wrote each stream.
    /// @param documentAccess Finds the pipelines.
    /// @param buildRecords   Records the pipelines' Events as validated.
    /// @param plan           Gives the plan's next step.
    @Inject
    CheckEventsTool(final EventChecks eventChecks,
                    final EventStreams eventStreams,
                    final StreamReader streamReader,
                    final DocumentAccess documentAccess,
                    final BuildRecords buildRecords,
                    final BuildPlan plan) {
        this.eventChecks = eventChecks;
        this.eventStreams = eventStreams;
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
        this.buildRecords = buildRecords;
        this.plan = plan;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Check event XML both ways: against the event-logging XSD held in this Stroom "
                             + "instance (each error with its line, element path and message), and against the "
                             + "quality rules beyond the schema (TimeCreated a full UTC timestamp; System Name, "
                             + "Environment, Generator, Device and TypeId present; exactly one action under "
                             + "EventDetail; no empty elements), per rule with the events failing it and examples. "
                             + "Give events_xml, or stream_ids for Events streams in Stroom.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(EVENTS_XML_ARG, "An <Events> document, e.g. step output. For what "
                                                           + "processing wrote, give stream_ids instead.")
                        .addStringProperty(SCHEMA_VERSION_ARG, "Event-logging version to validate against, e.g. "
                                                               + "'4.1.0'. Defaults to the version the document "
                                                               + "declares in xsi:schemaLocation or its Version, "
                                                               + "else the newest this Stroom holds.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Events streams processing wrote "
                                                                       + "(wait_for_processing lists them): the "
                                                                       + "server reads and checks their events "
                                                                       + "itself, up to " + MAX_RECORDS
                                                                       + " records in all, so they need not be "
                                                                       + "read and sent back."))
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // Read-only, although Events that pass are recorded as validated in their build's record, as the Stroom MCP
        // server's check_events did (it said readOnlyHint false for that). The record is the tools' own
        // bookkeeping, kept only for pipelines in a build (WriteGuard's own), never for anything a person wrote or
        // anything that runs: it changes no data, document content or processing, and promotion, which reads it,
        // asks for its own approval. Taken as a write, every check would need approving over /mcp, and couldn't be
        // made while the user is away.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("stream_ids are read as the user, who needs View Data and View on their feeds. Events that "
                      + "pass are recorded as validated in the build record of the pipeline that wrote them, when "
                      + "it is in a build.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String eventsXml = rawArg(call, EVENTS_XML_ARG);
        final String schemaVersion = stringArg(call, SCHEMA_VERSION_ARG);
        try {
            final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
            if (NullSafe.isEmptyString(eventsXml) == streamIds.isEmpty()) {
                return JsonResults.refused("Give events_xml (an <Events> document) or stream_ids (Events streams), "
                                           + "not both");
            }
            return JsonResults.of(streamIds.isEmpty()
                    ? check(eventsXml, schemaVersion)
                    : checkStreams(context, streamIds, schemaVersion));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ValidationException | StreamReader.StreamReadException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    /// @param eventsXml     An `<Events>` document, as sent.
    /// @param schemaVersion The event-logging version to validate against, or null.
    /// @return `ok`, `schema` (as [EventChecks#validateEvents] gives it) and `quality` (as
    /// [EventChecks#checkEventQuality] gives it, or a note that the rules don't apply to XML that isn't Events),
    /// and `note` if it came HTML-escaped.
    /// @throws ValidationException if it is not well-formed, or Stroom holds no such schema.
    Map<String, Object> check(final String eventsXml, final String schemaVersion) {
        final XsltChecks.Unescaped unescaped = XsltChecks.unescaped(eventsXml);
        if (!unescaped.warnings().isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>(check(unescaped.text(), schemaVersion));
            result.put("note", XsltChecks.ESCAPED);
            return result;
        }
        final Map<String, Object> schema = eventChecks.validateEvents(eventsXml, schemaVersion);
        final boolean valid = Boolean.TRUE.equals(schema.get("valid"));
        final Object which = schema.get("schema");
        final Map<String, Object> result = new LinkedHashMap<>();
        if (which == null || String.valueOf(which).contains("records")) {
            // Not Events (records:2 records, or a source's own XML): the event quality rules don't apply.
            final Map<String, Object> quality = new LinkedHashMap<>();
            quality.put("ok", valid);
            quality.put("note", "not event-logging Events: the event quality rules don't apply");
            result.put("ok", valid);
            result.put("schema", schema);
            result.put("quality", quality);
            return result;
        }
        final Map<String, Object> quality = EventChecks.checkEventQuality(eventsXml);
        result.put("ok", valid && Boolean.TRUE.equals(quality.get("ok")));
        result.put("schema", schema);
        result.put("quality", quality);
        return result;
    }

    // The events of the streams, read and checked; recorded as validated when they pass, with the plan's next step.
    private Map<String, Object> checkStreams(final ToolCallContext context,
                                             final List<Long> streamIds,
                                             final String schemaVersion) {
        final EventStreams.Read read = eventStreams.read(streamIds, MAX_RECORDS);
        final Map<String, Object> result = new LinkedHashMap<>(check(read.eventsXml(), schemaVersion));
        result.put("read", read.read());
        if (Boolean.TRUE.equals(result.get("ok"))) {
            final List<DocRef> recorded = recordValidated(streamIds);
            result.put("recorded_for", recorded.stream().map(DocRef::getName).toList());
            if (!recorded.isEmpty()) {
                // The plan moves on from here too (seen in testing: the agent's checklist stayed on processing).
                return plan.nextFor(context, recorded.getFirst(), result);
            }
        }
        return result;
    }

    /// The pipelines that wrote these Events streams, recorded as validated (the plan's `validated` step). Only
    /// pipelines in a build are recorded.
    ///
    /// @param streamIds Events streams.
    /// @return The pipelines recorded, by UUID.
    List<DocRef> recordValidated(final List<Long> streamIds) {
        final Set<String> pipelineUuids = new TreeSet<>();
        for (final Long streamId : streamIds) {
            streamReader.metaRow(streamId)
                    .map(MetaRow::getMeta)
                    .map(Meta::getPipelineUuid)
                    .filter(Objects::nonNull)
                    .ifPresent(pipelineUuids::add);
        }
        final List<DocRef> recorded = new ArrayList<>();
        for (final String uuid : pipelineUuids) {
            final Optional<DocRef> pipeline = documentAccess.findDocRef(uuid)
                    .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()));
            if (pipeline.isPresent() && buildRecords.recordPassed(pipeline.get(), BuildRecords.VALIDATED, null,
                    null).filter("recorded"::equals).isPresent()) {
                recorded.add(pipeline.get());
            }
        }
        return recorded;
    }
}
