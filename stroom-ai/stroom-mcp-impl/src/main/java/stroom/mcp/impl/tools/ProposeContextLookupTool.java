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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.ai.shared.AiToolCategory;
import stroom.data.shared.StreamTypeNames;
import stroom.mcp.impl.tools.ContextLookups.Entry;
import stroom.mcp.impl.tools.ContextLookups.Key;
import stroom.mcp.impl.tools.ContextLookups.Part;
import stroom.mcp.impl.tools.ContextProposal.Output;
import stroom.mcp.impl.tools.ContextProposal.Preview;
import stroom.security.shared.AppPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// `propose_context_lookup` - how a feed's records are enriched from the context data each stream part carries
/// (its `Context` child), when the user asks for it: the context's entries and their fields, the key (the entry fields,
/// up to three, whose values pick out exactly one entry for most records, and the record fields that match them) with
/// its match rate on the sample, and, for the outputs the agent proposes, a before and after for sample records. Once
/// the user has seen the table and agreed in a short form (`confirm`), it gives the pieces that build it: the context
/// loader's reference mapping (build_reference_xslt), the translation's mapping entries (build_translation_xslt) and
/// the pipeline reference (stream type Context), all keyed alike.
class ProposeContextLookupTool extends AbstractBuiltInTool {

    static final String NAME = "propose_context_lookup";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String ENTRIES_ARG = "entries";
    static final String MAP_ARG = "map";
    static final String KEY_FIELDS_ARG = "key_fields";
    static final String EVENT_KEY_FIELDS_ARG = "event_key_fields";
    static final String OUTPUTS_ARG = "outputs";
    static final String CONFIRM_ARG = "confirm";
    /// The map's name when none is given.
    static final String DEFAULT_MAP = "CONTEXT_ENTRY";
    // The status of a result whose table is for the user as it is.
    private static final String NEEDS_SHOWING = "needs_showing";
    // How much of each part is read, and the most parts and records.
    private static final int MAX_CHARS = 1_000_000;
    private static final int MAX_PARTS = 20;
    private static final int MAX_RECORDS = 500;
    // The sample records a preview shows.
    private static final int PREVIEWED = 2;

    private final StreamReader streamReader;
    private final Consent consent;

    @Inject
    ProposeContextLookupTool(final StreamReader streamReader, final Consent consent) {
        this.streamReader = streamReader;
        this.consent = consent;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean confirmsItself() {
        // The user agrees to the proposal in a form before its pieces are given.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .note("The sample streams and their context data are read as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Only when the user asks to enrich events from the context data their streams carry "
                             + "(each part's Context child): reads the sample's context entries and records, finds "
                             + "the key (up to 3 entry fields whose values pick out one entry for most records, and "
                             + "the record fields matching them) with its match rate, and previews the outputs you "
                             + "propose (text, fill_template, mask_names) for sample records, as a table for the "
                             + "user. Then call again with confirm=true: the user agrees in a short form, and the "
                             + "reply gives the context loader's mapping (build_reference_xslt), the mapping "
                             + "entries (build_translation_xslt) and the reference (stream_type 'Context').")
                .parameters(ObjectSchema.builder()
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Sample streams whose parts carry context "
                                                                       + "data."))
                        .addStringProperty(ENTRIES_ARG, "XML context: the entries' element path below its root, "
                                                        + "e.g. 'Event' (default: the root's most common child). "
                                                        + "JSON: one object's array of entries, e.g. 'events' "
                                                        + "(default: its longest), or '.' for the object itself.")
                        .addStringProperty(MAP_ARG, "The reference map's name (default " + DEFAULT_MAP + ").")
                        .addProperty(KEY_FIELDS_ARG, stringArraySchema("A key to use instead of the one found: "
                                                                       + "the entry fields, e.g. ['@Source', "
                                                                       + "'@Id'], with event_key_fields."))
                        .addProperty(EVENT_KEY_FIELDS_ARG, stringArraySchema("The record fields matching "
                                                                             + "key_fields, in order."))
                        .addProperty(OUTPUTS_ARG, ArraySchema.builder()
                                .description("What to fill from the entry a record picks out.")
                                .items(ObjectSchema.builder()
                                        .addStringProperty("path", "The Events path, e.g. "
                                                                   + "'EventDetail/Description'.")
                                        .addStringProperty("data_name", "For a path ending in Data, its Name.")
                                        .addStringProperty("from", "The entry value's path in an entry, e.g. "
                                                                   + "'Description'; for mask_names the table's "
                                                                   + "path (entry_tables), e.g. 'Flags/Flag'.")
                                        .addEnumProperty("render", ContextProposal.RENDERS, "text: as it is; "
                                                + "fill_template: a template whose numbered placeholders the "
                                                + "record's values fill; mask_names: the record's bit mask as the "
                                                + "names of the table's masks it holds.")
                                        .addStringProperty("pattern", "fill_template: the placeholder regex, its "
                                                                      + "first group the number, e.g. %(\\d+); "
                                                                      + "inferred from the templates if left "
                                                                      + "out.")
                                        .addStringProperty("values", "fill_template: the record's field giving "
                                                                     + "the values in order, e.g. "
                                                                     + "'Args/Arg'.")
                                        .addStringProperty("mask", "mask_names: the record's field holding the "
                                                                   + "mask.")
                                        .addStringProperty("mask_field", "mask_names: a table item's mask, e.g. "
                                                                         + "'@Mask' (left out for a JSON object "
                                                                         + "keyed by mask).")
                                        .addStringProperty("name_field", "mask_names: a table item's name, e.g. "
                                                                         + "'@Name'.")
                                        .addBooleanProperty("repeat", "mask_names: one element (Data) a name.")
                                        .addStringProperty("separator", "mask_names without repeat: between the "
                                                                        + "names (default ', ').")
                                        .required("path", "from")
                                        .build())
                                .build())
                        .addBooleanProperty(CONFIRM_ARG, "True once the user has seen the table: they agree in a "
                                                         + "short form, and the pieces are given.")
                        .required(STREAM_IDS_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(propose(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> propose(final ToolCall call, final ToolCallContext context) {
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        if (streamIds.isEmpty()) {
            throw new ToolRefusal("Give stream_ids: sample streams whose parts carry context data");
        }
        final String entriesPath = stringArg(call, ENTRIES_ARG);
        final String map = NullSafe.nonBlankStringElse(stringArg(call, MAP_ARG), DEFAULT_MAP).strip();
        final List<Part> parts = new ArrayList<>();
        ContextLookups.Context first = null;
        for (final long streamId : streamIds) {
            final int count = Math.min(MAX_PARTS, streamReader.partCount(streamId));
            for (int part = 0; part < count; part++) {
                final String contextText = streamReader.childText(streamId, part, StreamTypeNames.CONTEXT,
                        MAX_CHARS).map(StreamReader.RawText::text).orElse(null);
                if (contextText == null) {
                    continue;
                }
                final ContextLookups.Context read = ContextLookups.entries(contextText, entriesPath);
                if (first == null) {
                    first = read;
                } else if (!Objects.equals(first.jsonLayout(), read.jsonLayout())
                           || first.json() && !first.path().equals(read.path())) {
                    // One loader reads every part's context: they must be laid out alike.
                    throw new ToolRefusal("The parts' context data is laid out differently (" + first.describe()
                                          + "; part " + part + " of stream " + streamId + ": " + read.describe()
                                          + "): one context loader reads them all");
                }
                final String raw = streamReader.rawText(streamId, part, MAX_CHARS).text();
                parts.add(new Part(streamId, part, read.entries(), ContextLookups.records(raw, MAX_RECORDS)));
            }
        }
        if (first == null) {
            throw new ToolRefusal("No part of stream(s) " + streamIds + " carries context data (a Context child): "
                                  + "upload the sample with its context (upload_sample context=... or parts=[...]), "
                                  + "or ask the user where the context data is");
        }
        final List<String> contextFields = listOrNull(call, KEY_FIELDS_ARG);
        final List<String> eventFields = listOrNull(call, EVENT_KEY_FIELDS_ARG);
        if ((contextFields == null) != (eventFields == null)) {
            throw new ToolRefusal("Give key_fields and event_key_fields together, or neither (to find the key)");
        }
        final List<Key> keys = ContextLookups.keys(parts, contextFields, eventFields);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("parts", parts.size());
        result.put("entries", first.describe());
        result.put("entry_fields", entryFields(parts));
        result.put("entry_tables", entryTables(parts));
        result.put("keys", keys.stream().map(Key::toMap).toList());
        if (keys.isEmpty() || keys.getFirst().matched() == 0) {
            result.put("hint", "No combination of up to 3 entry fields picks out one entry for the records: look at "
                               + "entry_fields and the records (fetch_events), and give key_fields with "
                               + "event_key_fields, or ask the user what ties a record to its entry.");
            return result;
        }
        final Key key = keys.getFirst();
        final List<Output> outputs = ContextProposal.outputs(argsMap(call).get(OUTPUTS_ARG), parts);
        if (outputs.isEmpty()) {
            result.put("hint", "The key is " + key.contextFields() + " = " + key.eventFields() + " (" + key.percent()
                               + "% of records). Propose outputs for what the user asked (each path, from, render) "
                               + "and call again: the reply previews them for sample records.");
            return result;
        }
        final List<Map<String, Object>> previews = previews(parts, key, outputs);
        result.put("previews", previews);
        if (!confirming(call)) {
            result.put("status", NEEDS_SHOWING);
            result.put("table", table(key, previews));
            result.put("hint", (context.session().isChat()
                    ? "The chat shows the user this table itself: don't repeat it. "
                    : "Show the table to the user in your reply, as it is. ")
                               + "Then call again with the same arguments and confirm=true: a short form asks "
                               + "the user to agree.");
            return result;
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("key", String.join(" + ", key.contextFields()) + " = " + String.join(" + ", key.eventFields()));
        details.put("match rate", key.percent() + "% of " + key.records() + " sample records");
        details.put("outputs", outputs.size() + ", in the table shown");
        consent.require(call, context, Consent.Request.confirmation(NAME, "Fill " + outputs.size() + " output(s) "
                                                                          + "from the context data, map '" + map
                                                                          + "'", details));
        final Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("stream_type", StreamTypeNames.CONTEXT);
        reference.put("loader_pipeline", "<the context loader pipeline>");
        result.remove("previews");
        result.put("confirmed", true);
        result.put("reference_mapping", ContextProposal.referenceMapping(map, first, key, outputs));
        result.put("mapping_entries", ContextProposal.mappingEntries(map, first, key, outputs));
        result.put("reference", reference);
        result.put("hint", "1. build_reference_xslt mapping=reference_mapping, save_xslt it in the build. 2. "
                           + "create_pipeline from find_reference_data's context_loader_template, its "
                           + "translationFilter.xslt that XSLT and combinedParser.type '" + (first.json()
                ? "JSON"
                : "XML") + "' (set_properties; no text converter" + (first.json()
                ? ", and no other parser property: its JSON reader wraps the data in a root map, which "
                  + "reference_mapping's records allow for"
                : "") + "): the context loader. 3. Add mapping_entries to the "
                           + "translation's mapping (build_translation_xslt). 4. Give the events pipeline "
                           + "references=[reference] with loader_pipeline the context loader (create_pipeline or "
                           + "update_pipeline). 5. step_sample: records whose key finds no entry, placeholders left "
                           + "unfilled and bits with no name are warned of.");
        return result;
    }

    // Each output for the first records whose key picks out an entry, from as many parts as there are.
    private static List<Map<String, Object>> previews(final List<Part> parts,
                                                      final Key key,
                                                      final List<Output> outputs) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (int round = 0; round < MAX_RECORDS && out.size() < PREVIEWED; round++) {
            for (final Part part : parts) {
                if (out.size() >= PREVIEWED || round >= part.records().size()) {
                    continue;
                }
                final Map<String, List<String>> record = part.records().get(round);
                final Entry entry = ContextLookups.matched(part, key, record);
                if (entry == null) {
                    continue;
                }
                final Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("stream", part.stream());
                preview.put("part", part.part());
                preview.put("record", round);
                final List<String> keyValues = key.eventFields().stream()
                        .map(field -> record.getOrDefault(field, List.of("")).getFirst())
                        .toList();
                preview.put("key", keyValues);
                final List<Map<String, Object>> filled = new ArrayList<>();
                for (final Output output : outputs) {
                    final Preview one = ContextProposal.preview(output, entry, record);
                    final Map<String, Object> shown = new LinkedHashMap<>();
                    shown.put("output", output.label());
                    shown.put("before", one.input());
                    shown.put("after", one.values());
                    if (!one.said().isEmpty()) {
                        shown.put("reported", one.said());
                    }
                    filled.add(shown);
                }
                preview.put("outputs", filled);
                out.add(preview);
            }
        }
        return out;
    }

    // The key and the previews as a table for the chat.
    @SuppressWarnings("unchecked")
    private static String table(final Key key, final List<Map<String, Object>> previews) {
        final List<String> lines = new ArrayList<>();
        lines.add("## Context lookup: key " + code(String.join(" + ", key.contextFields())) + " = "
                  + code(String.join(" + ", key.eventFields())) + ", matching " + key.matched() + " of "
                  + key.records() + " sample records (" + key.percent() + "%)");
        lines.add("");
        lines.add("| Record | Output | Before | After |");
        lines.add("| --- | --- | --- | --- |");
        for (final Map<String, Object> preview : previews) {
            final String where = "stream " + preview.get("stream") + " part " + preview.get("part") + " record "
                                 + preview.get("record");
            for (final Map<String, Object> output : (List<Map<String, Object>>) preview.get("outputs")) {
                lines.add("| " + where + " | " + code(String.valueOf(output.get("output"))) + " | "
                          + cell(output.get("before")) + " | " + cell(output.get("after")) + " |");
            }
        }
        return String.join("\n", lines);
    }

    private static String code(final String text) {
        return "`" + text.replace("`", "'").replace("|", "\\|") + "`";
    }

    private static String cell(final Object value) {
        final String text = value instanceof final List<?> list
                ? String.join("; ", list.stream().map(String::valueOf).toList())
                : String.valueOf(value);
        final String one = text.replace("\n", " ").replace("|", "\\|");
        return one.length() > 160
                ? one.substring(0, 157) + "..."
                : one;
    }

    private static Map<String, Object> entryFields(final List<Part> parts) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Part part : parts) {
            for (final Entry entry : part.entries()) {
                entry.fields().forEach((name, value) -> out.putIfAbsent(name, shortened(value)));
            }
        }
        return out;
    }

    private static Map<String, Object> entryTables(final List<Part> parts) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Part part : parts) {
            for (final Entry entry : part.entries()) {
                entry.tables().forEach((name, items) -> {
                    if (!items.isEmpty()) {
                        out.putIfAbsent(name, items.getFirst());
                    }
                });
            }
        }
        return out;
    }

    private static String shortened(final String value) {
        return value.length() > 120
                ? value.substring(0, 117) + "..."
                : value;
    }

    private List<String> listOrNull(final ToolCall call, final String name) {
        final List<String> values = stringListArg(call, name);
        return values.isEmpty()
                ? null
                : values;
    }

    private boolean confirming(final ToolCall call) {
        final Object given = argsMap(call).get(CONFIRM_ARG);
        return Boolean.TRUE.equals(given) || "true".equals(String.valueOf(given));
    }
}
