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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// `summarise_streams` - summarises streams: their errors (grouped by severity, element and message, each group
/// triaged, the pipeline's own elements told from inherited ones) or their events (counts per EventDetail type,
/// TypeId and Action, and how often each event-logging path is populated).
///
/// Read as the user, who needs the View Data permission: only streams of feeds they may view are read. It reads
/// many streams, so a call takes one of the user's run slots.
class SummariseStreamsTool extends AbstractBuiltInTool {

    static final String NAME = "summarise_streams";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String KIND_ARG = "kind";
    static final String MAX_EVENTS_ARG = "max_events";
    static final String ERRORS = "errors";
    static final String EVENTS = "events";
    static final int DEFAULT_MAX_EVENTS = 200;
    static final int MAX_MAX_EVENTS = 2_000;

    private final StreamSummaries streamSummaries;
    private final DocumentAccess documentAccess;
    private final AiAuditLog auditLog;

    @Inject
    SummariseStreamsTool(final StreamSummaries streamSummaries,
                         final DocumentAccess documentAccess,
                         final AiAuditLog auditLog) {
        this.streamSummaries = streamSummaries;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Summarise streams: their errors (grouped by severity, element and message, each "
                             + "group triaged, the pipeline's own elements told from inherited ones) or their "
                             + "events (counts per EventDetail type, TypeId and Action, and how often each "
                             + "event-logging path is populated). Use it to document what a pipeline produces, to "
                             + "triage an Error stream, or to find fields rarely filled.")
                .parameters(ObjectSchema.builder()
                        .addProperty(STREAM_IDS_ARG, ArraySchema.builder()
                                .items(new IntegerSchema(null))
                                .description("Streams to summarise.")
                                .build())
                        .addEnumProperty(KIND_ARG, List.of(ERRORS, EVENTS), "errors: the error markers of Error "
                                + "streams (or the Error children of raw or Events streams), grouped and triaged "
                                + "as blocking, review or benign; events: what Events streams hold, by "
                                + "EventDetail type, TypeId and Action, and how often each path is populated.")
                        .addIntegerProperty(MAX_EVENTS_ARG, "events: how many to read (default "
                                                            + DEFAULT_MAX_EVENTS + ", at most " + MAX_MAX_EVENTS
                                                            + ").")
                        .required(STREAM_IDS_ARG, KIND_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It reads many streams.
        return true;
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final List<Long> streamIds;
        try {
            streamIds = streamIds(call);
        } catch (final IllegalArgumentException e) {
            return JsonResults.refused("Error: " + e.getMessage());
        }
        final String kind = stringArg(call, KIND_ARG);
        if (!ERRORS.equals(kind) && !EVENTS.equals(kind)) {
            return JsonResults.refused("Error: kind must be errors or events.");
        }
        if (!documentAccess.canViewData()) {
            return JsonResults.refused("Error: you don't have permission to view data, so streams can't be "
                                    + "summarised.");
        }
        final int maxEvents = Math.max(1, Math.min(MAX_MAX_EVENTS,
                Objects.requireNonNullElse(intArg(call, MAX_EVENTS_ARG), DEFAULT_MAX_EVENTS)));
        final Map<String, Object> result;
        try {
            result = EVENTS.equals(kind)
                    ? streamSummaries.summariseEvents(streamIds, maxEvents)
                    : summariseErrors(streamIds);
        } catch (final RuntimeException e) {
            return JsonResults.refused("Error: " + StreamSummaries.reason(e));
        } finally {
            auditLog.streamsViewed(context.session(), streamIds, "Summarised the " + kind + " of "
                                                                 + streamIds.size() + " stream(s) with the "
                                                                 + NAME + " tool");
        }
        // The by-action shares are for documenting fields, too much to read here.
        final Map<String, Object> shown = new LinkedHashMap<>(result);
        shown.remove(StreamSummaries.POPULATION_BY_ACTION);
        return JsonResults.of(shown);
    }

    private Map<String, Object> summariseErrors(final List<Long> streamIds) {
        final Map<String, Object> streams = new LinkedHashMap<>();
        for (final long streamId : streamIds) {
            try {
                streams.put(String.valueOf(streamId), streamSummaries.summariseErrors(streamId));
            } catch (final RuntimeException e) {
                // One stream that isn't there, or can't be read: the others are still summarised.
                final Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("stream_id", streamId);
                failed.put("error", StreamSummaries.reason(e));
                streams.put(String.valueOf(streamId), failed);
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("streams", streams);
        return result;
    }

    /// @return The stream ids, given as a list, one number, or numbers between commas.
    /// @throws IllegalArgumentException if there are none, or one isn't a whole number.
    static List<Long> streamIds(final ToolCall call) {
        final List<Long> ids = new ArrayList<>();
        for (final String item : stringListArg(call, STREAM_IDS_ARG)) {
            try {
                final double number = Double.parseDouble(item);
                if (number != Math.rint(number)) {
                    throw new NumberFormatException(item);
                }
                ids.add((long) number);
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("'" + item + "' isn't a stream id: give whole numbers, e.g. "
                                                   + "from stream_meta.");
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Give stream_ids: the streams to summarise.");
        }
        return ids;
    }
}
