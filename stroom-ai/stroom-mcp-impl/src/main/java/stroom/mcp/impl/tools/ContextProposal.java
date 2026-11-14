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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.FillTemplate;
import stroom.ai.impl.tools.generation.MaskNames;
import stroom.mcp.impl.tools.ContextLookups.Entry;
import stroom.mcp.impl.tools.ContextLookups.Key;
import stroom.mcp.impl.tools.ContextLookups.Part;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What the agent proposes to fill from context data, and what that makes: each output (an Events path, and the
/// entry value it is filled from, as text, as a template filled from the record's values, or as a bit mask's names
/// from a table in the entry), a preview of it for sample records, and the pieces that build it, the reference
/// mapping of the context loader (build_reference_xslt) and the translation's mapping entries (build_translation_xslt),
/// keyed alike.
final class ContextProposal {

    /// An output's value as it is.
    static final String TEXT = "text";
    /// An output's value a template whose placeholders the record's values fill.
    static final String FILL_TEMPLATE = "fill_template";
    /// An output's value the names of the masks a record's bit mask holds.
    static final String MASK_NAMES = "mask_names";
    /// What an output can be.
    static final List<String> RENDERS = List.of(TEXT, FILL_TEMPLATE, MASK_NAMES);
    /// The placeholder patterns a template is tried against, when none is given: numbered placeholders as message
    /// texts commonly write them.
    static final List<String> PLACEHOLDERS = List.of("%(\\d+)", "\\{(\\d+)\\}", "\\$(\\d+)");

    private ContextProposal() {
        // Static only.
    }

    /// @param given The outputs as the call gives them.
    /// @param parts The parts, to infer a template's placeholder pattern from its entries.
    /// @return The outputs, checked.
    /// @throws ToolRefusal if one is wrong.
    static List<Output> outputs(final Object given, final List<Part> parts) {
        if (!(given instanceof final List<?> list) || list.isEmpty()) {
            return List.of();
        }
        final List<Output> out = new ArrayList<>();
        final Set<String> elements = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof final Map<?, ?> map)) {
                throw new ToolRefusal("outputs[" + i + "] is {path, from, render, ...}");
            }
            final String where = "outputs[" + i + "]";
            final String path = text(map.get("path"));
            final String from = text(map.get("from"));
            final String render = text(map.get("render")) == null
                    ? TEXT
                    : text(map.get("render"));
            if (path == null || from == null || !RENDERS.contains(render)) {
                throw new ToolRefusal(where + " needs path (the Events path), from (the entry value's path in a "
                                      + "context entry) and render (" + String.join(", ", RENDERS) + ")");
            }
            String pattern = text(map.get("pattern"));
            final String values = text(map.get("values"));
            final String mask = text(map.get("mask"));
            final String maskField = text(map.get("mask_field"));
            final String nameField = text(map.get("name_field"));
            if (FILL_TEMPLATE.equals(render)) {
                if (values == null) {
                    throw new ToolRefusal(where + ": fill_template needs values, the record's field (or xpath) "
                                          + "giving the placeholders' values in order, e.g. 'Args/Arg'");
                }
                if (pattern == null) {
                    pattern = inferPattern(parts, from);
                }
            }
            if (MASK_NAMES.equals(render) && (mask == null || maskField == null || nameField == null)) {
                throw new ToolRefusal(where + ": mask_names needs mask (the record's field holding the bit mask), "
                                      + "and mask_field and name_field (the paths of a table item's mask and name, "
                                      + "e.g. '@Mask' and '@Name'); from is the table's items' path in the entry");
            }
            out.add(new Output(path, text(map.get("data_name")), from, render, pattern, values, mask, maskField,
                    nameField, Boolean.TRUE.equals(map.get("repeat")) || "true".equals(text(map.get("repeat"))),
                    text(map.get("separator")), element(from, elements)));
        }
        return List.copyOf(out);
    }

    /// @param output An output.
    /// @param entry  The entry a record picks out.
    /// @param record The record.
    /// @return What the record gives the output: its input (as the record or entry has it) and the value written,
    /// and what stepping would report (placeholders unfilled, bits with no name).
    static Preview preview(final Output output, final Entry entry, final Map<String, List<String>> record) {
        if (MASK_NAMES.equals(output.render())) {
            final String value = first(record.get(output.mask()));
            final List<Map.Entry<String, String>> table = new ArrayList<>();
            for (final Map<String, String> item : entry.tables().getOrDefault(output.from(), List.of())) {
                table.add(new AbstractMap.SimpleImmutableEntry<>(item.get(output.maskField()),
                        item.get(output.nameField())));
            }
            final MaskNames.Named named = MaskNames.named(value, table);
            final List<String> said = new ArrayList<>();
            if (named.notANumber()) {
                said.add("'" + value + "' is no bit mask");
            }
            named.unnamed().forEach(bit -> said.add("no name for bit " + bit + " (" + MaskNames.hex(bit) + ")"));
            return new Preview(value, output.repeat()
                    ? named.names()
                    : List.of(String.join(separator(output), named.names())), said);
        }
        final String value = entry.fields().get(output.from());
        if (FILL_TEMPLATE.equals(output.render())) {
            final FillTemplate.Filled filled = FillTemplate.fill(value, output.pattern(),
                    record.getOrDefault(output.values(), List.of()));
            return new Preview(value, filled.text() == null
                    ? List.of()
                    : List.of(filled.text()), filled.unfilled().stream()
                    .map(placeholder -> "no value for " + placeholder)
                    .toList());
        }
        return new Preview(value, value == null
                ? List.of()
                : List.of(value), List.of());
    }

    /// @param map       The map's name.
    /// @param context   The context's entries and where they are.
    /// @param key       The key.
    /// @param outputs   The outputs.
    /// @return The context loader's reference mapping, for build_reference_xslt.
    static Map<String, Object> referenceMapping(final String map,
                                                final ContextLookups.Context context,
                                                final Key key,
                                                final List<Output> outputs) {
        final List<Map<String, Object>> values = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final Output output : outputs) {
            if (!seen.add(output.element())) {
                continue;
            }
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("element", output.element());
            if (MASK_NAMES.equals(output.render())) {
                value.put("xpath", output.from());
                value.put("copy", true);
            } else {
                value.put("field", output.from());
            }
            values.add(value);
        }
        final Map<String, Object> mapEntry = new LinkedHashMap<>();
        mapEntry.put("name", map);
        mapEntry.put("key_fields", key.contextFields());
        mapEntry.put("values", values);
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("input", "xml");
        out.put("record", "*/" + context.path());
        if (context.namespace() != null && !context.namespace().isEmpty()) {
            out.put("xml_namespace", context.namespace());
        }
        out.put("maps", List.of(mapEntry));
        return out;
    }

    /// @param map     The map's name.
    /// @param key     The key.
    /// @param outputs The outputs.
    /// @return The translation's mapping entries that fill them, for build_translation_xslt.
    static List<Map<String, Object>> mappingEntries(final String map, final Key key, final List<Output> outputs) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Output output : outputs) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", output.path());
            if (output.dataName() != null) {
                entry.put("data_name", output.dataName());
            }
            if (MASK_NAMES.equals(output.render())) {
                entry.put(ContextLookups.plainPath(output.mask())
                        ? "field"
                        : "xpath", output.mask());
                if (output.repeat()) {
                    entry.put("repeat", true);
                } else if (output.separator() != null) {
                    entry.put("separator", output.separator());
                }
                final Map<String, Object> names = new LinkedHashMap<>();
                names.put("lookup", lookup(map, key, output.element() + "/" + lastStep(output.from())));
                names.put("mask", output.maskField());
                names.put("name", output.nameField());
                entry.put("mask_names", names);
            } else {
                entry.put("lookup", lookup(map, key, output.element()));
                if (FILL_TEMPLATE.equals(output.render())) {
                    final Map<String, Object> fill = new LinkedHashMap<>();
                    fill.put("pattern", output.pattern());
                    fill.put(ContextLookups.plainPath(output.values())
                            ? "field"
                            : "xpath", output.values());
                    entry.put("fill_template", fill);
                }
            }
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> lookup(final String map, final Key key, final String path) {
        final Map<String, Object> lookup = new LinkedHashMap<>();
        lookup.put("map", map);
        lookup.put("key_fields", key.eventFields());
        lookup.put("path", path);
        return lookup;
    }

    // The placeholder pattern most of the entries' templates hold, of those commonly written; %(\d+) when none.
    private static String inferPattern(final List<Part> parts, final String from) {
        String best = PLACEHOLDERS.getFirst();
        long most = 0;
        for (final String candidate : PLACEHOLDERS) {
            final Pattern compiled = Pattern.compile(candidate);
            long count = 0;
            for (final Part part : parts) {
                for (final Entry entry : part.entries()) {
                    final String template = entry.fields().get(from);
                    if (template != null) {
                        final Matcher matcher = compiled.matcher(template);
                        while (matcher.find()) {
                            count++;
                        }
                    }
                }
            }
            if (count > most) {
                most = count;
                best = candidate;
            }
        }
        return best;
    }

    // The reference value's element an output's entry value is written to: named after it, each once.
    private static String element(final String from, final Set<String> taken) {
        String base = lastStep(from).replace("@", "").replaceAll("\\W", "_").toLowerCase(Locale.ROOT);
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "value_" + base;
        }
        String name = base;
        for (int n = 2; taken.contains(name); n++) {
            name = base + "_" + n;
        }
        taken.add(name);
        return name;
    }

    private static String lastStep(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String separator(final Output output) {
        return output.separator() == null
                ? ", "
                : output.separator();
    }

    private static String first(final List<String> values) {
        return values == null || values.isEmpty()
                ? null
                : values.getFirst();
    }

    private static String text(final Object value) {
        return value == null || "".equals(value)
                ? null
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// One output filled from context data.
    ///
    /// @param path      The Events path, e.g. `EventDetail/Description`.
    /// @param dataName  For a path ending in Data, the Data's Name; else null.
    /// @param from      The entry value's path in a context entry; for mask_names, the table items' path.
    /// @param render    [#TEXT], [#FILL_TEMPLATE] or [#MASK_NAMES].
    /// @param pattern   fill_template: the placeholder pattern.
    /// @param values    fill_template: the record's field (or xpath) giving the values in order.
    /// @param mask      mask_names: the record's field holding the mask.
    /// @param maskField mask_names: a table item's mask path.
    /// @param nameField mask_names: a table item's name path.
    /// @param repeat    mask_names: one element a name.
    /// @param separator mask_names without repeat: what goes between the names.
    /// @param element   The reference value's element it is loaded into.
    record Output(String path,
                  String dataName,
                  String from,
                  String render,
                  String pattern,
                  String values,
                  String mask,
                  String maskField,
                  String nameField,
                  boolean repeat,
                  String separator,
                  String element) {

        /// @return Its label: the path, and the Data's Name.
        String label() {
            return dataName == null
                    ? path
                    : path + " (" + dataName + ")";
        }
    }

    /// An output's preview for one record.
    ///
    /// @param input  Its input: the entry's value, or the record's mask.
    /// @param values The values written.
    /// @param said   What stepping would report.
    record Preview(String input, List<String> values, List<String> said) {

    }
}
