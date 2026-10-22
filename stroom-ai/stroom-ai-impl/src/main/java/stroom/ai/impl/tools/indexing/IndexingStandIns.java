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

import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.util.json.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

// STAND-IN until the indexing core port is merged.
/// The parts of the Stroom MCP server's `utils/fieldplan.py`, `utils/templatecheck.py` and `utils/ecs.py` that the
/// field-mapping documentation ([stroom.ai.impl.tools.fielddoc]) needs, written as the smallest stand-ins that
/// compile and behave as the Python does for those uses. The indexing core port replaces each with its real
/// class (`FieldPlan`, `TemplateCheck`, ...) when it is merged.
public final class IndexingStandIns {

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.ELASTIC`: the Elasticsearch type each logical type is mapped as.
    public static final Map<String, String> ELASTIC = elastic();

    // STAND-IN until the indexing core port is merged.
    /// Where `utils/ecs.py` reads the Elastic Common Schema from (`conventions/ecs_fields.json`), on the class path.
    public static final String ECS_FIELDS = "/stroom/mcp/impl/knowledge/conventions/ecs_fields.json";

    private static final Pattern ACTION = Pattern.compile("^EventDetail/Network/[^/]+/(?=.)");

    private static volatile Map<String, Object> ecsFields;

    private IndexingStandIns() {
        // Static only.
    }

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.source_matches(source: str, path: str) -> bool`: whether a plan source names this event path:
    /// `*` stands for one element, `//` for any elements between.
    ///
    /// @param source A plan field's source.
    /// @param path   An event path.
    /// @return True if the source names the path.
    public static boolean sourceMatches(final String source, final String path) {
        if (!wild(source)) {
            return source.equals(path);
        }
        if (!source.contains("//")) {
            final String[] want = strip(source).split("/", -1);
            final String[] have = strip(path).split("/", -1);
            if (want.length != have.length) {
                return false;
            }
            for (int i = 0; i < want.length; i++) {
                if (!"*".equals(want[i]) && !want[i].equals(have[i])) {
                    return false;
                }
            }
            return true;
        }
        final String pattern = Pattern.quote(strip(source))
                .replace("*", "\\E[^/]+\\Q")
                .replace("//", "\\E/(?:[^/]+/)*\\Q");
        return Pattern.compile((source.startsWith("//")
                ? "(?:[^/]+/)*"
                : "") + pattern).matcher(strip(path)).matches();
    }

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.population_of(source: str, population: dict[str, float]) -> float | None`: how often a source is
    /// populated, a wildcard's paths summed (an event has one Network action).
    ///
    /// @param source     A plan field's source.
    /// @param population The share of events (%) each event path is populated in.
    /// @return The source's share, or null if no path it names is populated.
    public static Double populationOf(final String source, final Map<String, Double> population) {
        if (!wild(source)) {
            return population.get(source);
        }
        double sum = 0;
        boolean hit = false;
        for (final Map.Entry<String, Double> entry : population.entrySet()) {
            if (sourceMatches(source, entry.getKey())) {
                sum += entry.getValue();
                hit = true;
            }
        }
        return hit
                ? Math.min(100.0, sum)
                : null;
    }

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.any_action(path: str) -> str`: a Network path with its action as `*`.
    ///
    /// @param path An event path.
    /// @return It with a Network action as `*`.
    public static String anyAction(final String path) {
        return ACTION.matcher(path).replaceFirst("EventDetail/Network/*/");
    }

    // STAND-IN until the indexing core port is merged.
    /// `templatecheck.json_xml_documents(xml: str) -> list[dict[str, Any]]`: documents from an indexing XSLT's
    /// output, `<array>` of `<map>`, or a single `<map>`. Maps are [LinkedHashMap]s, arrays [List]s, `null` is
    /// null, and every other value a [JsonScalar] (Python's `('number', text)` tuples).
    ///
    /// @param xml The output.
    /// @return Its documents; none if it doesn't parse.
    public static List<Map<String, Object>> jsonXmlDocuments(final String xml) {
        final XmlElement root;
        try {
            root = XmlParser.parse(xml.replaceFirst("^\\s*<\\?xml[^>]*\\?>", ""));
        } catch (final XmlParser.XmlParseException e) {
            return List.of();
        }
        final Object data = value(root);
        final List<Map<String, Object>> documents = new ArrayList<>();
        final List<?> items = data instanceof final List<?> list
                ? list
                : java.util.Collections.singletonList(data);
        for (final Object item : items) {
            if (item instanceof final Map<?, ?> map) {
                @SuppressWarnings("unchecked") final Map<String, Object> document = (Map<String, Object>) map;
                documents.add(document);
            }
        }
        return documents;
    }

    // STAND-IN until the indexing core port is merged.
    /// `templatecheck.flatten_document(doc: dict[str, Any], prefix: str = '') -> dict[str, list[Any]]`: path to
    /// values, dotted keys and nested maps both becoming dotted paths; a map as `JsonScalar("object", null)`.
    ///
    /// @param document A document from [#jsonXmlDocuments(String)].
    /// @return Its values by dotted path.
    public static Map<String, List<Object>> flattenDocument(final Map<String, Object> document) {
        final Map<String, List<Object>> out = new LinkedHashMap<>();
        document.forEach((key, item) -> add(out, key, item));
        return out;
    }

    // STAND-IN until the indexing core port is merged.
    /// `ecs.field(name).get('short')`, as `fielddoc.ecs_says(name: str) -> str` reads it: what the Elastic Common
    /// Schema says of a field of that name.
    ///
    /// @param name A field name, e.g. `source.ip`.
    /// @return ECS's short description, or "" if it is not an ECS field (or ECS isn't on the class path).
    public static String ecsShort(final String name) {
        final Object fields = ecs().get("fields");
        if (fields instanceof final Map<?, ?> map && map.get(name) instanceof final Map<?, ?> field
            && field.get("short") instanceof final String text) {
            return text;
        }
        return "";
    }

    private static Map<String, Object> ecs() {
        Map<String, Object> loaded = ecsFields;
        if (loaded == null) {
            loaded = readEcs();
            ecsFields = loaded;
        }
        return loaded;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readEcs() {
        try (final InputStream in = IndexingStandIns.class.getResourceAsStream(ECS_FIELDS)) {
            if (in == null) {
                return Map.of();
            }
            return JsonUtil.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), Map.class);
        } catch (final IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private static void add(final Map<String, List<Object>> out, final String path, final Object item) {
        if (item instanceof final Map<?, ?> map) {
            out.computeIfAbsent(path, k -> new ArrayList<>()).add(new JsonScalar("object", null));
            map.forEach((key, sub) -> add(out, path + "." + key, sub));
        } else if (item instanceof final List<?> list) {
            list.forEach(sub -> add(out, path, sub));
        } else if (item != null) {
            out.computeIfAbsent(path, k -> new ArrayList<>()).add(item);
        }
    }

    private static Object value(final XmlElement node) {
        final String kind = node.localName();
        switch (kind) {
            case "map" -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                for (final XmlElement child : node.childElements()) {
                    map.put(child.get("key"), value(child));
                }
                return map;
            }
            case "array" -> {
                final List<Object> list = new ArrayList<>();
                for (final XmlElement child : node.childElements()) {
                    list.add(value(child));
                }
                return list;
            }
            case "null" -> {
                return null;
            }
            default -> {
                final String text = Objects.requireNonNullElse(node.text(), "");
                return new JsonScalar("number".equals(kind) || "boolean".equals(kind)
                        ? kind
                        : "string", text);
            }
        }
    }

    private static boolean wild(final String source) {
        return source.contains("*") || source.contains("//");
    }

    private static String strip(final String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }

    private static Map<String, String> elastic() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("id", "long");
        map.put("keyword", "keyword");
        map.put("text", "text");
        map.put("date", "date");
        map.put("long", "long");
        map.put("double", "double");
        map.put("boolean", "boolean");
        map.put("ip", "ip");
        return java.util.Collections.unmodifiableMap(map);
    }


    // --------------------------------------------------------------------------------


    // STAND-IN until the indexing core port is merged.
    /// A JSON value that is not a map, list or null, as `json_xml_documents` gives it: its JSON type and its text
    /// (Python's `('string', 'alice')`).
    ///
    /// @param kind `string`, `number`, `boolean`, or `object` (a map, in [#flattenDocument(Map)]).
    /// @param text Its text; null for an `object`.
    public record JsonScalar(String kind, String text) {

    }

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.PlannedField` (the fields the documentation reads): `name: str`, `type: LogicalType`,
    /// `source: str`, `description: str = ''`.
    ///
    /// @param name        Field name in the index, e.g. `UserId` or `user.name`.
    /// @param type        Its logical type, e.g. `keyword`.
    /// @param source      XPath from the Event element, e.g. `EventSource/User/Id` or `@StreamId`.
    /// @param description What it holds, or "".
    public record PlannedField(String name, String type, String source, String description) {

        /// Fills in "" for a null description.
        public PlannedField {
            description = Objects.requireNonNullElse(description, "");
        }

        /// @param name   Field name in the index.
        /// @param type   Its logical type.
        /// @param source XPath from the Event element.
        /// @return The field, with no description.
        public static PlannedField of(final String name, final String type, final String source) {
            return new PlannedField(name, type, source, "");
        }
    }

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.Discovery` (the fields the documentation reads): how a discovery index's raw records become
    /// documents.
    ///
    /// @param timestampField  The source field holding the record's time.
    /// @param timestampFormat Its format, or null.
    /// @param meta            Index field -> stream meta attribute.
    /// @param drop            Top-level source fields left out.
    /// @param unpackJson      Whether a string holding a JSON object is also indexed parsed, as `<field>_json`.
    /// @param ignoreAbove     Strings longer than this are not indexed.
    /// @param totalFieldsLimit Elasticsearch's limit on mapped fields.
    public record Discovery(String timestampField, String timestampFormat, Map<String, String> meta,
                            List<String> drop, boolean unpackJson, long ignoreAbove, long totalFieldsLimit) {

        /// Fills in empty collections for null ones.
        public Discovery {
            meta = Objects.requireNonNullElse(meta, Map.of());
            drop = Objects.requireNonNullElse(drop, List.of());
        }

        /// @param timestampField The source field holding the record's time.
        /// @return A discovery with the Python model's defaults otherwise.
        public static Discovery of(final String timestampField) {
            return new Discovery(timestampField, null, Map.of(), List.of(), true, 1024, 2000);
        }
    }

    // STAND-IN until the indexing core port is merged.
    /// `fieldplan.FieldPlan` (the fields the documentation reads): an index's plan.
    ///
    /// @param backend   `lucene` or `elasticsearch`.
    /// @param indexName Lucene index doc name, or Elasticsearch index / data stream name.
    /// @param timeField Name of the field holding the event time.
    /// @param fields    The fields.
    /// @param dropWhen  Conditions on an Event that leave it out of the index.
    /// @param discovery Set for a discovery index, else null.
    /// @param shared    Shared templates that write fields.
    public record FieldPlan(String backend, String indexName, String timeField, List<PlannedField> fields,
                            List<String> dropWhen, Discovery discovery, List<SharedTemplate> shared) {

        /// Fills in empty lists for null ones.
        public FieldPlan {
            fields = Objects.requireNonNullElse(fields, List.of());
            dropWhen = Objects.requireNonNullElse(dropWhen, List.of());
            shared = Objects.requireNonNullElse(shared, List.of());
        }

        /// @param backend   `lucene` or `elasticsearch`.
        /// @param indexName The index name.
        /// @param timeField The time field.
        /// @param fields    The fields.
        /// @return A plan with nothing else set.
        public static FieldPlan of(final String backend, final String indexName, final String timeField,
                                   final List<PlannedField> fields) {
            return new FieldPlan(backend, indexName, timeField, fields, List.of(), null, List.of());
        }

        /// `written_by(name: str) -> SharedTemplate | None`.
        ///
        /// @param name A field name.
        /// @return The shared template that writes this field (or the object holding it), or null.
        public SharedTemplate writtenBy(final String name) {
            return shared.stream()
                    .filter(use -> name.equals(use.at()) || name.startsWith(use.at() + "."))
                    .findFirst()
                    .orElse(null);
        }
    }
}
