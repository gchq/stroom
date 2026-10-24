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

package stroom.ai.impl.tools.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// A translation mapping: which input field (or constant) goes to which event-logging path, per kind of event,
/// from which [XsltGenerator] writes the translation XSLT. Ported from the Stroom MCP server's `TranslationMapping`
/// model; its JSON is the server's, field for field (it is kept in the XSLT doc's description), so a mapping saved by
/// either reads in the other.
///
/// Read with [#fromJson(Map)], which validates it as pydantic does (with pydantic's messages, which the agent is
/// shown), and turns its pairs into extract entries, each rule's data list into Data entries, a meta entry into the
/// xpath reading it, and Data Names into the style's `data_names`. Written with [#toJson(DumpMode)].
///
/// @param input        What the XSLT reads: `data_splitter`, `json`, `xml` or `xml_fragments`.
/// @param root         Root element to match, or null for the input's default.
/// @param record       Record elements under the root, or null for the input's default.
/// @param xmlNamespace `xml_namespace`: xml and xml_fragments, the records' namespace, if any.
/// @param jsonLayout   `json_layout`: json input only, `array` or `lines`.
/// @param jsonFields   `json_fields`: input fields holding JSON text.
/// @param xmlFields    `xml_fields`: input fields holding XML text.
/// @param pairs        Key/value pairs in a text field; always empty once validated (they become extract
///                     entries).
/// @param extract      Fields parsed out of text fields with regular expressions.
/// @param forEach      `for_each`: when one record holds several events, the field or XPath selecting the items.
/// @param nilValues    `nil_values`: values the source writes for 'none', e.g. `-`.
/// @param dropWhen     `drop_when`: records (items) to leave untranslated, tried before the event rules.
/// @param common       Fields every event gets.
/// @param events       Event kinds, tried in order (at least one).
/// @param unmatched    Records no rule matches: `warn` or `skip`.
/// @param style        How the XSLT is written.
/// @param imports      Shared XSLTs imported (xsl:import).
/// @param shared       Named templates from shared XSLTs, called in their element's place.
/// @param functions    Shared XSLTs whose xsl:functions the xpaths call.
public record TranslationMapping(String input,
                                 String root,
                                 String record,
                                 String xmlNamespace,
                                 String jsonLayout,
                                 List<String> jsonFields,
                                 List<String> xmlFields,
                                 List<KeyValuePairs> pairs,
                                 List<Extraction> extract,
                                 String forEach,
                                 List<String> nilValues,
                                 List<DropRule> dropWhen,
                                 List<FieldMapping> common,
                                 List<EventRule> events,
                                 String unmatched,
                                 XsltStyle style,
                                 List<String> imports,
                                 List<SharedTemplate> shared,
                                 List<SharedFunctions> functions) {

    /// The kinds of input.
    public static final List<String> INPUTS = List.of("data_splitter", "json", "xml", "xml_fragments");

    /// Fills in the defaults for nulls.
    public TranslationMapping {
        xmlNamespace = Objects.requireNonNullElse(xmlNamespace, "");
        jsonLayout = Objects.requireNonNullElse(jsonLayout, "array");
        jsonFields = copy(jsonFields);
        xmlFields = copy(xmlFields);
        pairs = copy(pairs);
        extract = copy(extract);
        nilValues = copy(nilValues);
        dropWhen = copy(dropWhen);
        common = copy(common);
        events = copy(events);
        unmatched = Objects.requireNonNullElse(unmatched, "warn");
        style = Objects.requireNonNullElse(style, XsltStyle.DEFAULT);
        imports = copy(imports);
        shared = copy(shared);
        functions = copy(functions);
    }

    /// @param json A mapping as JSON-like values (maps, lists, strings, numbers, booleans and nulls), e.g. as
    ///             kept in an XSLT doc's description.
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate, with every error pydantic would give.
    public static TranslationMapping fromJson(final Map<String, Object> json) {
        return parse(json);
    }

    /// @param json A mapping as JSON-like values, which may be anything (an error says what it should be).
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate, with every error pydantic would give.
    public static TranslationMapping parse(final Object json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("TranslationMapping", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static TranslationMapping read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "TranslationMapping");
        if (fields == null) {
            return null;
        }
        final TranslationMapping mapping = new TranslationMapping(
                fields.requiredLiteral("input", INPUTS),
                fields.optionalString("root"),
                fields.optionalString("record"),
                fields.string("xml_namespace", ""),
                fields.literal("json_layout", "array", List.of("array", "lines")),
                fields.strings("json_fields"),
                fields.strings("xml_fields"),
                fields.models("pairs", (value, at) -> KeyValuePairs.read(reader, value, at)),
                fields.models("extract", (value, at) -> Extraction.read(reader, value, at)),
                fields.optionalString("for_each"),
                fields.strings("nil_values"),
                fields.models("drop_when", (value, at) -> DropRule.read(reader, value, at)),
                fields.models("common", (value, at) -> FieldMapping.read(reader, value, at)),
                fields.requiredModels("events", (value, at) -> EventRule.read(reader, value, at), 1),
                fields.literal("unmatched", "warn", List.of("warn", "skip")),
                fields.model("style", (value, at) -> XsltStyle.read(reader, value, at), XsltStyle.DEFAULT),
                fields.strings("imports"),
                fields.models("shared", (value, at) -> SharedTemplate.read(reader, value, at)),
                fields.models("functions", (value, at) -> SharedFunctions.read(reader, value, at)));
        if (!fields.ok()) {
            return null;
        }
        return mapping.withPairsAsExtract().withDataNamesInStyle();
    }

    // Each key an extract entry, as the draft writes them: the generator, the checks and the kept mapping see
    // extract alone. A text another extraction gives is read after that one.
    private TranslationMapping withPairsAsExtract() {
        if (pairs.isEmpty()) {
            return this;
        }
        final List<Extraction> entries = new ArrayList<>(extract);
        int front = 0;
        for (final KeyValuePairs pair : pairs) {
            final List<Extraction> added = new ArrayList<>();
            for (final String key : pair.keys()) {
                final String named = pair.names().get(key);
                added.add(new Extraction(pair.field(), null, Pairs.pairRegex(pair.style(), key),
                        List.of(named == null || named.isEmpty()
                                ? Pairs.keyName(key)
                                : named), "", null));
            }
            int giving = -1;
            for (int n = 0; n < entries.size(); n++) {
                if (entries.get(n).names().contains(pair.field())) {
                    giving = n;
                }
            }
            final int at = giving < 0
                    ? front
                    : giving + 1;
            entries.addAll(at, added);
            if (giving < 0) {
                front += added.size();
            }
        }
        return new TranslationMapping(input, root, record, xmlNamespace, jsonLayout, jsonFields, xmlFields, List.of(),
                entries, forEach, nilValues, dropWhen, common, events, unmatched, style, imports, shared, functions);
    }

    // Applied to the mapping itself, so the XSLT, the documentation's tables and the sample checks all use the names
    // the events will hold, and the kept mapping holds them.
    private TranslationMapping withDataNamesInStyle() {
        if ("as_given".equals(style.dataNames())) {
            return this;
        }
        final List<EventRule> rules = events.stream()
                .map(rule -> rule.withFields(namedInStyle(rule.fields())))
                .toList();
        return new TranslationMapping(input, root, record, xmlNamespace, jsonLayout, jsonFields, xmlFields, pairs,
                extract, forEach, nilValues, dropWhen, namedInStyle(common), rules, unmatched, style, imports, shared,
                functions);
    }

    private List<FieldMapping> namedInStyle(final List<FieldMapping> entries) {
        return entries.stream()
                .map(entry -> entry.dataName() == null || entry.dataName().isEmpty()
                        ? entry
                        : entry.withDataName(XsltNames.dataNameIn(entry.dataName(), style.dataNames())))
                .map(this::parentInStyle)
                .toList();
    }

    // The Data an entry is written in (data_parent, not in the Python model) named in the style too, as its own.
    private FieldMapping parentInStyle(final FieldMapping entry) {
        if (entry.dataParent() == null) {
            return entry;
        }
        final String parent = Arrays.stream(entry.dataParent().split("/", -1))
                .map(name -> XsltNames.dataNameIn(name, style.dataNames()))
                .collect(Collectors.joining("/"));
        return entry.withDataParent(parent);
    }

    /// @param events The event rules.
    /// @return This mapping with those rules.
    public TranslationMapping withEvents(final List<EventRule> events) {
        return new TranslationMapping(input, root, record, xmlNamespace, jsonLayout, jsonFields, xmlFields, pairs,
                extract, forEach, nilValues, dropWhen, common, events, unmatched, style, imports, shared, functions);
    }

    /// @return Every field mapping: the common ones, then each rule's.
    public List<FieldMapping> allEntries() {
        final List<FieldMapping> out = new ArrayList<>(common);
        events.forEach(rule -> out.addAll(rule.fields()));
        return out;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it; [DumpMode#COMPACT] is the form kept in a
    /// document's description.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("input", input, null)
                .put("root", root, null)
                .put("record", record, null)
                .put("xml_namespace", xmlNamespace, "")
                .put("json_layout", jsonLayout, "array")
                .put("json_fields", jsonFields, List.of())
                .put("xml_fields", xmlFields, List.of())
                .models("pairs", pairs, pair -> pair.toJson(mode))
                .models("extract", extract, extraction -> extraction.toJson(mode))
                .put("for_each", forEach, null)
                .put("nil_values", nilValues, List.of())
                .models("drop_when", dropWhen, rule -> rule.toJson(mode))
                .models("common", common, entry -> entry.toJson(mode))
                .models("events", events, rule -> rule.toJson(mode))
                .put("unmatched", unmatched, "warn")
                .model("style", style, XsltStyle.DEFAULT, given -> given.toJson(mode))
                .put("imports", imports, List.of())
                .models("shared", shared, template -> template.toJson(mode))
                .models("functions", functions, function -> function.toJson(mode))
                .build();
    }

    private static <T> List<T> copy(final List<T> list) {
        return List.copyOf(Objects.requireNonNullElse(list, List.of()));
    }
}
