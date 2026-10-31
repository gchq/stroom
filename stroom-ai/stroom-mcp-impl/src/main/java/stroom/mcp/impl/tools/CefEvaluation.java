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

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.cef.CefKeys;
import stroom.ai.impl.tools.cef.CefLines;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// evaluate_pipeline's `cef` section for a CEF output pipeline (Events in, CEF lines out, to Kafka or a file): its
/// lines, stepped with the Events they came from as draft_cef_mapping and write_documentation step them, reviewed by
/// [CefLines#review] against ArcSight's dictionary (judged by the choice kept with its XSLT's plan, when there is
/// one): header fields still holding a TODO constant, what is wrong with its lines, the Event values no key carries
/// and the mapping its lines imply.
class CefEvaluation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CefEvaluation.class);

    /// The most records stepped for the review.
    static final int RECORDS = 100;
    /// The most items shown of each list.
    static final int SHOWN = 15;
    /// The most kinds of event shown of the implied mapping.
    static final int KINDS_SHOWN = 10;

    private final DraftCefMappingTool cef;
    private final KeptMappings keptMappings;

    /// @param cef          Steps a CEF pipeline, reading its lines and the Events they came from.
    /// @param keptMappings Reads the CEF plan kept with the pipeline's XSLT.
    @Inject
    CefEvaluation(final DraftCefMappingTool cef, final KeptMappings keptMappings) {
        this.cef = cef;
        this.keptMappings = keptMappings;
    }

    /// The `cef` section.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The CEF pipeline.
    /// @param streamIds    The Events streams to step it over; none if there are none to step.
    /// @param backend      `kafka` when it sends to Kafka, `file` when it writes its lines as text, else null.
    /// @return `status`, `output`, `lines_stepped`, `custom_keys`, `header_todo`, `problems`, `not_sent`,
    /// `implied_mapping` and any `notes`; or `status` and `reason` when it couldn't be reviewed.
    Map<String, Object> cef(final ToolCallContext context,
                            final String pipelineUuid,
                            final List<Long> streamIds,
                            final String backend) {
        if (streamIds.isEmpty()) {
            return status("not_checked", "No Events streams to step it over: give stream_ids (Events streams it "
                                         + "sends).");
        }
        final Optional<KeptMappings.KeptXslt> kept = kept(pipelineUuid);
        final DraftCefMappingTool.Stepped stepped;
        try {
            stepped = cef.stepped(context, pipelineUuid, streamIds, kept.map(KeptMappings.KeptXslt::element)
                    .orElse(null), RECORDS);
        } catch (final ToolRefusal | StreamReader.StreamReadException e) {
            return status("unavailable", e.getMessage());
        }
        if (stepped.lines().isEmpty()) {
            return status("no_lines", "Stepping it over streams " + streamIds + " gave no CEF lines at element '"
                                      + stepped.element() + "': check they are Events streams it sends.");
        }
        final Boolean customKeys = kept.map(found -> CefPlan.fromJson(found.kept().payload()).customKeys())
                .orElse(null);
        final List<XmlElement> events = stepped.events().stream().filter(Objects::nonNull).toList();
        final Map<String, Object> reviewed = CefLines.review(stepped.lines(), events, customKeys);
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", "checked");
        section.put("output", stepped.sent());
        section.put("lines_stepped", stepped.lines().size());
        section.put("custom_keys", customKeys == null
                ? "not known: its XSLT keeps no CEF plan, so keys outside ArcSight's dictionary are reported, not "
                  + "judged"
                : customKeys);
        section.put("header_todo", headerTodo(stepped.lines().getFirst()));
        section.put("problems", shown(reviewed.get("problems")));
        section.put("not_sent", shown(reviewed.get("not_sent")));
        section.put("implied_mapping", implied(reviewed.get("implied_mapping")));
        final List<String> notes = new ArrayList<>();
        if (stepped.stopped()) {
            notes.add("Stepping stopped before the session's time limit: the review is of the lines stepped.");
        }
        if ("kafka".equals(backend) || CefPlan.KAFKA.equals(stepped.sent().get("output"))) {
            notes.add("It sends to Kafka: delivery to Kafka (and ArcSight) is not tested by stepping.");
        } else if (PipelineShapes.FILE.equals(backend)) {
            notes.add("It writes its lines as text: what its appender does with them (the file or stream written, "
                      + "and how ArcSight collects it) is not tested by stepping.");
        }
        if (!notes.isEmpty()) {
            section.put("notes", notes);
        }
        return section;
    }

    /// @param line A CEF line.
    /// @return Each header field still holding a TODO constant, as `{field, value}`.
    static List<Map<String, Object>> headerTodo(final String line) {
        final List<String> header = CefLines.parse(line).header();
        final List<Map<String, Object>> todo = new ArrayList<>();
        for (int i = 0; i < CefKeys.HEADER.size() && i + 1 < header.size(); i++) {
            final String value = header.get(i + 1);
            if (value != null && value.strip().startsWith("TODO")) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("field", CefKeys.HEADER.get(i).said());
                entry.put("value", ToolText.head(value, 200));
                todo.add(entry);
            }
        }
        return todo;
    }

    // The plan kept with its XSLT, if a CEF one.
    private Optional<KeptMappings.KeptXslt> kept(final String pipelineUuid) {
        try {
            return keptMappings.keptXslt(pipelineUuid)
                    .filter(found -> XsltSaves.CEF.equals(found.kept().kind()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No CEF plan read for {}", pipelineUuid), e);
            return Optional.empty();
        }
    }

    // The implied mapping, short: up to KINDS_SHOWN kinds of event, each with up to SHOWN keys.
    private static Map<String, Object> implied(final Object mapping) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (mapping instanceof final Map<?, ?> byKind) {
            byKind.entrySet().stream().limit(KINDS_SHOWN).forEach(entry -> out.put(String.valueOf(entry.getKey()),
                    shown(entry.getValue())));
            if (byKind.size() > KINDS_SHOWN) {
                out.put("more_kinds", byKind.size() - KINDS_SHOWN);
            }
        }
        return out;
    }

    private static List<?> shown(final Object list) {
        if (list instanceof final List<?> items) {
            return items.subList(0, Math.min(SHOWN, items.size()));
        }
        return List.of();
    }

    private static Map<String, Object> status(final String status, final String reason) {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", status);
        section.put("reason", reason);
        return section;
    }
}
