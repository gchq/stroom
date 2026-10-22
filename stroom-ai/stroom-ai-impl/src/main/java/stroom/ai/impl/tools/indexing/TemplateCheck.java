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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.Globs;
import stroom.ai.impl.tools.IpAddresses;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.indexing.TemplateMappings.Mapping;
import stroom.ai.impl.tools.validation.CloseMatches;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/// Checks an Elasticsearch index template against the documents an indexing pipeline writes. The documents come
/// from stepping the candidate indexing pipeline: its XSLT's JSON XML output, the form Stroom's
/// ElasticIndexingFilter sends. Each document field is compared with the template's mapping: missing fields under
/// the template's dynamic setting, values the mapped type cannot take, parent/child clashes, and fields the template
/// expects but the pipeline never writes (often renamed in the template). Every mismatch comes back as a change to
/// make, to the pipeline or to the template. Ported from the Stroom MCP server's `utils/templatecheck.py` (compare,
/// json_xml_documents, flatten_document).
public final class TemplateCheck {

    private static final String FN = "http://www.w3.org/2005/xpath-functions";
    private static final Set<String> KEYWORD_LIKE = JsonValues.nullSafeSet("keyword", "text", "wildcard",
            "match_only_text", "constant_keyword", "version", "search_as_you_type");
    private static final Set<String> INTEGER = JsonValues.nullSafeSet("long", "integer", "short", "byte",
            "unsigned_long");
    private static final Set<String> DECIMAL = JsonValues.nullSafeSet("double", "float", "half_float", "scaled_float");
    private static final Set<String> OBJECT = JsonValues.nullSafeSet("object", "nested", "flattened");
    private static final List<String> CHECKED_FORMATS = List.of("strict_date_optional_time", "date_optional_time",
            "strict_date_time", "date_time", "strict_date_optional_time_nanos", "epoch_millis", "epoch_second");
    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNIX_LINES;
    private static final Pattern ISO = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(T\\d{2}(:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?)?(Z|[+-]\\d{2}(:?\\d{2})?)?)?$", FLAGS);
    private static final Pattern WHOLE = Pattern.compile("^-?\\d+$", FLAGS);
    private static final Pattern NUMBER = Pattern.compile("^-?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$", FLAGS);
    private static final Pattern DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>", FLAGS);

    private TemplateCheck() {
        // Static only.
    }

    /// @param xml An indexing XSLT's output: `<array>` of `<map>`, or a single `<map>`.
    /// @return The documents, each value as read ([DocValue]: its kind, number, boolean or string, and its text),
    /// a map as an object, an array as a list, a null as null; none if the output isn't XML.
    public static List<Map<String, Object>> jsonXmlDocuments(final String xml) {
        final XmlElement root;
        try {
            root = XmlParser.parse(DECLARATION.matcher(xml).replaceFirst(""));
        } catch (final RuntimeException e) {
            return List.of();
        }
        final Object data = value(root);
        final List<Map<String, Object>> documents = new ArrayList<>();
        for (final Object item : data instanceof final List<?> list
                ? list
                : List.of(data)) {
            final Map<String, Object> document = JsonValues.map(item);
            if (document != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    private static Object value(final XmlElement node) {
        final String kind = node.localName();
        if ("map".equals(kind)) {
            final Map<String, Object> map = new LinkedHashMap<>();
            node.childElements().forEach(child -> map.put(child.get("key"), value(child)));
            return map;
        }
        if ("array".equals(kind)) {
            final List<Object> list = new ArrayList<>();
            node.childElements().forEach(child -> list.add(value(child)));
            return list;
        }
        if ("null".equals(kind)) {
            return null;
        }
        final String text = node.text() == null
                ? ""
                : node.text();
        return new DocValue("number".equals(kind) || "boolean".equals(kind)
                ? kind
                : "string", text);
    }

    /// @param doc    A document as read.
    /// @param prefix The path it is below, or empty.
    /// @return Each path's values, with dotted keys and nested maps both becoming dotted paths; maps as `object`.
    public static Map<String, List<DocValue>> flattenDocument(final Map<String, Object> doc, final String prefix) {
        final Map<String, List<DocValue>> out = new LinkedHashMap<>();
        doc.forEach((key, item) -> add(out, prefix.isEmpty()
                ? PyText.str(key)
                : prefix + "." + PyText.str(key), item));
        return out;
    }

    private static void add(final Map<String, List<DocValue>> out, final String path, final Object item) {
        if (item instanceof final Map<?, ?> map) {
            out.computeIfAbsent(path, key -> new ArrayList<>()).add(DocValue.OBJECT);
            map.forEach((key, sub) -> add(out, path + "." + PyText.str(key), sub));
        } else if (item instanceof final List<?> list) {
            list.forEach(sub -> add(out, path, sub));
        } else if (item != null) {
            out.computeIfAbsent(path, key -> new ArrayList<>()).add(item instanceof final DocValue read
                    ? read
                    : new DocValue("string", PyText.str(item)));
        }
    }

    /// Checks an index template against the documents an indexing pipeline writes.
    ///
    /// @param body      The index template's body (or a mapping, as `{template: {mappings}}`).
    /// @param docs      The documents, as read by [#jsonXmlDocuments(String)].
    /// @param indexName The pipeline's index, or null.
    /// @param ecs       The Elastic Common Schema, for a template composing ecs@mappings.
    /// @return `{compatible, blocking, pipeline_changes, notes, documents_checked, fields_written}`, as the
    /// Stroom MCP server's compare gives it.
    public static Map<String, Object> compare(final Map<String, Object> body,
                                              final List<Map<String, Object>> docs,
                                              final String indexName,
                                              final EcsSchema ecs) {
        final List<String> blocking = new ArrayList<>();
        final List<Map<String, Object>> changes = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final Object givenPatterns = body.get("index_patterns");
        final List<String> patterns = givenPatterns instanceof final String one
                ? List.of(one)
                : JsonValues.strings(givenPatterns);
        if (!body.containsKey("index_patterns")) {
            notes.add("a mapping, not an index template: which indices it covers is not checked");
        } else if (JsonValues.truthy(indexName) && patterns.stream().noneMatch(p -> Globs.matches(indexName, p))) {
            blocking.add("index_patterns " + PyText.list(patterns) + " do not match the pipeline's index '"
                         + indexName + "', so this template would not apply to it");
            changes.add(change(null, "template does not cover index '" + indexName + "'",
                    "change index_patterns to include '" + indexName + "*', or set the indexing pipeline's "
                    + "indexName to an index they match"));
        }
        final Map<String, Object> mappings = TemplateMappings.mappingsOf(body);
        final Mapping mapping = TemplateMappings.readMapping(mappings);
        if (body.get("data_stream") != null) {
            // Elasticsearch maps a data stream's @timestamp as a date itself, whatever dynamic says.
            mapping.fields().putIfAbsent("@timestamp", new LinkedHashMap<>(Map.of("type", "date")));
        }
        final boolean byComponent = JsonValues.strings(body.get("composed_of")).contains(EcsSchema.COMPONENT);
        checkMapping(mapping, blocking, changes, notes);
        final Map<String, List<DocValue>> emitted = new LinkedHashMap<>();
        for (final Map<String, Object> doc : docs) {
            flattenDocument(doc, "").forEach((path, values) ->
                    emitted.computeIfAbsent(path, key -> new ArrayList<>()).addAll(values));
        }
        if (body.get("data_stream") != null && !emitted.containsKey("@timestamp")) {
            blocking.add("the template makes a data stream, which needs @timestamp, but the pipeline does not write "
                         + "it");
            changes.add(change("@timestamp", "missing for a data stream",
                    "write @timestamp from EventTime/TimeCreated in the indexing XSLT"));
        }
        final List<String> unmapped = new ArrayList<>();
        final Set<String> disabled = TemplateMappings.notIndexed(mappings).objects();
        for (final String path : new TreeSet<>(emitted.keySet())) {
            final List<DocValue> values = emitted.get(path);
            if (disabled.stream().anyMatch(o -> path.startsWith(o + "."))) {
                // Under an object the template doesn't parse: kept in _source as it is, nothing to map.
                continue;
            }
            Map<String, Object> spec = mapping.fields().get(path);
            if (spec != null && "alias".equals(spec.get("type"))) {
                blocking.add(path + ": written by the pipeline, but mapped as an alias (of '"
                             + PyText.str(spec.get("path")) + "'), and documents can't write to an alias, so they "
                             + "are rejected");
                changes.add(change(path, "written, but mapped as an alias", "map '" + path + "' as a field in the "
                        + "template (as '" + PyText.str(spec.get("path")) + "' is mapped), or stop writing it in the "
                        + "indexing XSLT"));
                continue;
            }
            if (spec == null && byComponent && "true".equals(mapping.dynamicFor(path))
                && ecs.componentType(path) != null) {
                // Left to ecs@mappings, which maps it as the document brings it: checked as it maps it.
                spec = new LinkedHashMap<>(Map.of("type", ecs.componentType(path)));
            }
            if (spec == null) {
                if (!clash(path, mapping, blocking, changes)
                    && values.stream().anyMatch(value -> !"object".equals(value.kind()))) {
                    unmapped.add(path);
                }
                continue;
            }
            checkValues(path, spec, values, blocking, changes, notes);
        }
        final List<String> expected = new ArrayList<>();
        mapping.fields().forEach((path, spec) -> {
            if (!OBJECT.contains(spec.getOrDefault("type", "object")) && !"alias".equals(spec.get("type"))
                && !emitted.containsKey(path)) {
                expected.add(path);
            }
        });
        for (final String path : unmapped) {
            unmappedField(path, mapping.dynamicFor(path), expected, byComponent, ecs, blocking, changes, notes);
        }
        final List<String> hidden = TemplateMappings.unsearchable(mappings, emitted.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(value -> !"object".equals(value.kind())))
                .map(Map.Entry::getKey)
                .toList());
        if (!hidden.isEmpty()) {
            notes.add(PyText.list(hidden.subList(0, Math.min(20, hidden.size())))
                      + (hidden.size() > 20
                    ? " (+" + (hidden.size() - 20) + ")"
                    : "")
                      + ": written by the pipeline, but not indexed by the template (index: false, or under an object "
                      + "with enabled: false): kept in _source, but Stroom lists such a field as not searchable, so "
                      + "they can't be verification checks. Say so to the user");
        }
        for (final String path : expected) {
            changes.add(change(path, "in the template but the pipeline never writes it", "add '" + path + "' to the "
                    + "indexing XSLT (from the right event-logging path), or drop it from the template"));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("compatible", blocking.isEmpty());
        result.put("blocking", blocking);
        result.put("pipeline_changes", changes);
        result.put("notes", notes);
        result.put("documents_checked", (long) docs.size());
        result.put("fields_written", new ArrayList<>(new TreeSet<>(emitted.keySet())));
        return result;
    }

    // The template's own problems: an alias of a field it doesn't map, a date format not checked here.
    private static void checkMapping(final Mapping mapping,
                                     final List<String> blocking,
                                     final List<Map<String, Object>> changes,
                                     final List<String> notes) {
        mapping.fields().forEach((path, spec) -> {
            final Object target = spec.get("path");
            if ("alias".equals(spec.get("type"))) {
                final Object targetType = JsonValues.truthy(target)
                        ? JsonValues.mapOrEmpty(mapping.fields().get(PyText.str(target))).get("type")
                        : null;
                if (!JsonValues.truthy(target) || targetType == null || "alias".equals(targetType)
                    || "object".equals(targetType)) {
                    // Not checked before: a template with such an alias was agreed, and Elasticsearch refused it.
                    blocking.add(path + ": an alias of '" + PyText.str(target) + "', which this template doesn't "
                                 + "map as a field, so Elasticsearch refuses the template ('an alias must refer to an "
                                 + "existing field')");
                    changes.add(change(path, "alias of a field the template lacks ('" + PyText.str(target) + "')",
                            "drop the alias '" + path + "' from the template, or map '" + PyText.str(target)
                            + "'"));
                }
            }
            final Object format = spec.get("format");
            if (("date".equals(spec.get("type")) || "date_nanos".equals(spec.get("type"))) && JsonValues.truthy(format)
                && List.of(PyText.str(format).split("\\|\\|", -1)).stream()
                        .anyMatch(f -> !CHECKED_FORMATS.contains(PyText.strip(f)))) {
                notes.add(path + ": custom date format " + PyText.repr(format) + " not checked here; stepping "
                          + "output is ISO 8601");
            }
        });
    }

    // A field the template doesn't map, below one it maps as a value: true if so (and said).
    private static boolean clash(final String path,
                                 final Mapping mapping,
                                 final List<String> blocking,
                                 final List<Map<String, Object>> changes) {
        final String parent = TemplateMappings.parents(path).stream()
                .filter(p -> mapping.fields().containsKey(p)
                             && !OBJECT.contains(mapping.fields().get(p).getOrDefault("type", "object")))
                .findFirst()
                .orElse(null);
        if (parent == null) {
            return false;
        }
        final String type = PyText.str(mapping.fields().get(parent).get("type"));
        blocking.add(path + ": the template maps '" + parent + "' as " + type + ", so it cannot also hold '" + path
                     + "'");
        changes.add(change(path, "'" + parent + "' is a " + type, "rename '" + path + "' in the indexing XSLT, or map '"
                                                                  + parent + "' as an object"));
        return true;
    }

    // A mapped field's values: a type Stroom can't list, and the first value the type can't take.
    private static void checkValues(final String path,
                                    final Map<String, Object> spec,
                                    final List<DocValue> values,
                                    final List<String> blocking,
                                    final List<Map<String, Object>> changes,
                                    final List<String> notes) {
        final Object type = spec.get("type");
        if (EcsSchema.STROOM_UNLISTED.contains(type) && values.stream().anyMatch(v -> !"object".equals(v.kind()))) {
            // Indexed, but Stroom lists no field of this type: nothing in Stroom can search or show it.
            notes.add(path + ": mapped as " + type + ", a type Stroom leaves out of the Elastic Index doc's fields, "
                      + "so Stroom can't search or show it");
            changes.add(change(path, "mapped as " + type + ", which Stroom can't search", "map '" + path + "' in the "
                    + "template as " + ("match_only_text".equals(type) || "search_as_you_type".equals(type)
                    ? "text"
                    : "keyword") + " (ahead of any component template), so Stroom lists it"));
        }
        for (final DocValue value : values) {
            final String why = fits(spec, value.kind(), value.text() == null
                    ? ""
                    : value.text());
            if (why != null) {
                blocking.add(path + " " + why);
                changes.add(change(path, why, "change the indexing XSLT to write '" + path + "' as a valid "
                                              + PyText.str(type) + ", or map it with a type that takes these "
                                              + "values"));
                return;
            }
        }
    }

    // A field written but not mapped, as the template's dynamic setting has it.
    private static void unmappedField(final String path,
                                      final String dynamic,
                                      final List<String> expected,
                                      final boolean byComponent,
                                      final EcsSchema ecs,
                                      final List<String> blocking,
                                      final List<Map<String, Object>> changes,
                                      final List<String> notes) {
        final List<String> renamed = CloseMatches.closeMatches(path, expected, 1, CloseMatches.DEFAULT_CUTOFF);
        if (!renamed.isEmpty()) {
            changes.add(change(path, "not in the template, which has '" + renamed.getFirst() + "' instead",
                    "rename '" + path + "' to '" + renamed.getFirst() + "' in the indexing XSLT"));
            expected.remove(renamed.getFirst());
            if ("strict".equals(dynamic)) {
                blocking.add(path + ": not in the template and dynamic is strict, so documents are rejected");
            }
            return;
        }
        if (byComponent && !"true".equals(dynamic) && ecs.componentType(path) != null) {
            // An ECS field left to ecs@mappings, which maps only as documents bring fields: off, it maps nothing.
            final String said = path + ": an ECS field left to " + EcsSchema.COMPONENT + ", but dynamic is " + dynamic
                                + ", so it isn't mapped" + ("strict".equals(dynamic)
                    ? " and documents are rejected"
                    : " (kept in _source, not searchable)");
            ("strict".equals(dynamic)
                    ? blocking
                    : notes).add(said);
            changes.add(change(path, "left to " + EcsSchema.COMPONENT + ", but dynamic: " + dynamic,
                    "set the template's dynamic back to true, so " + EcsSchema.COMPONENT + " maps it as ECS says, or "
                    + "map '" + path + "' in the template"));
        } else if ("strict".equals(dynamic)) {
            blocking.add(path + ": not in the template and dynamic is strict, so documents are rejected");
            changes.add(change(path, "not mapped (dynamic: strict)", "add '" + path + "' to the template, or stop "
                                                                     + "writing it in the indexing XSLT"));
        } else if ("false".equals(dynamic) || "runtime".equals(dynamic)) {
            notes.add(path + ": written by the pipeline but not mapped (dynamic: " + dynamic + "), so it is kept in "
                      + "_source but not searchable");
        } else {
            notes.add(path + ": not mapped; Elasticsearch will add it with a guessed type (dynamic mapping)");
        }
    }

    /// @param spec A field's mapping.
    /// @param kind A value's kind as read (`string`, `number`, `boolean` or `object`).
    /// @param text Its text.
    /// @return Why the value doesn't suit the mapped type, or null if it does (or a custom date format, reported
    /// apart as unchecked).
    static String fits(final Map<String, Object> spec, final String kind, final String text) {
        final Object mapped = spec.getOrDefault("type", "object");
        if (OBJECT.contains(mapped)) {
            return "object".equals(kind)
                    ? null
                    : "is a single value, but the template maps it as " + PyText.str(mapped);
        }
        if ("object".equals(kind)) {
            return "is an object, but the template maps it as " + PyText.str(mapped);
        }
        if (KEYWORD_LIKE.contains(mapped)) {
            final Object value = spec.get("value");
            if ("constant_keyword".equals(mapped) && value != null && !text.equals(value)) {
                return "is " + PyText.repr(text) + ", but constant_keyword only takes " + PyText.repr(value);
            }
            return null;
        }
        if (INTEGER.contains(mapped)) {
            return WHOLE.matcher(text).lookingAt()
                    ? null
                    : "is " + PyText.repr(text) + ", not a whole number (" + mapped + ")";
        }
        if (DECIMAL.contains(mapped)) {
            return NUMBER.matcher(text).lookingAt()
                    ? null
                    : "is " + PyText.repr(text) + ", not a number (" + mapped + ")";
        }
        if ("boolean".equals(mapped)) {
            return "true".equals(text) || "false".equals(text)
                    ? null
                    : "is " + PyText.repr(text) + ", not true/false";
        }
        if ("ip".equals(mapped)) {
            return IpAddresses.isAddress(text)
                    ? null
                    : "is " + PyText.repr(text) + ", not an IP address";
        }
        if ("date".equals(mapped) || "date_nanos".equals(mapped)) {
            return fitsDate(spec, text);
        }
        return null;
    }

    private static String fitsDate(final Map<String, Object> spec, final String text) {
        final List<String> formats = List.of(PyText.str(spec.getOrDefault("format",
                "strict_date_optional_time||epoch_millis")).split("\\|\\|", -1)).stream().map(PyText::strip).toList();
        final List<Pattern> known = new ArrayList<>();
        for (final String format : formats) {
            if (CHECKED_FORMATS.contains(format)) {
                known.add(format.startsWith("epoch")
                        ? WHOLE
                        : ISO);
            }
        }
        if (known.size() < formats.size()) {
            // A custom format: reported separately as unchecked.
            return null;
        }
        return known.stream().anyMatch(pattern -> pattern.matcher(text).lookingAt())
                ? null
                : "is " + PyText.repr(text) + ", which date format " + String.join("||", formats) + " rejects";
    }

    private static Map<String, Object> change(final String field, final String problem, final String change) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("field", field);
        entry.put("problem", problem);
        entry.put("change", change);
        return entry;
    }


    // --------------------------------------------------------------------------------


    /// A value of a document as read from the indexing XSLT's output: Python's `(kind, text)`.
    ///
    /// @param kind `string`, `number`, `boolean`, or `object` for a map.
    /// @param text Its text (null for a map).
    public record DocValue(String kind, String text) {

        /// A map, as flattened.
        public static final DocValue OBJECT = new DocValue("object", null);
    }
}
