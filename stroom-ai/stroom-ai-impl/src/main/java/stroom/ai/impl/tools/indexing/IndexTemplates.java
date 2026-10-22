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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.PyJson.JsonDecodeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Elasticsearch index and component templates as the user pastes them (Kibana Dev Tools requests, GET output,
/// Stroom's elastic_index_templates output, or an index's mapping), kept to what a PUT takes, and written back as a
/// Dev Tools request laid out to read. Ported from the Stroom MCP server's `utils/templatecheck.py` (as_put,
/// stroom_tool_templates, parse_template, dev_tools, parse_component_templates).
///
/// A text that can't be read is refused with an [IllegalArgumentException] saying why, as the server's ValueError
/// did (its message is the agent's to act on).
public final class IndexTemplates {

    /// What a PUT of an index template takes (checked on Elasticsearch 9.5). A GET also returns what Elasticsearch
    /// sets itself, which a PUT refuses: created_date_millis and modified_date_millis ("a template property which is
    /// managed by the system"), created_date and modified_date with ?human, and the lifecycle's rollover with
    /// ?include_defaults.
    public static final List<String> INDEX_TEMPLATE_FIELDS = List.of("index_patterns", "composed_of", "template",
            "priority", "version", "_meta", "data_stream", "allow_auto_create", "ignore_missing_component_templates",
            "deprecated");
    /// What a PUT of a component template takes.
    public static final List<String> COMPONENT_TEMPLATE_FIELDS = List.of("template", "version", "_meta",
            "deprecated");

    private static final List<String> TEMPLATE_FIELDS = List.of("settings", "mappings", "aliases", "lifecycle",
            "data_stream_options");
    // Stroom's elastic_index_templates tool: each template's body as Elasticsearch returns it, in a json code block
    // below a heading naming it, among lines of prose. A part Stroom couldn't read has a line saying why instead of
    // a block.
    private static final Pattern STROOM_HEADING = Pattern.compile(
            "^#{1,6}[ \\t]*(Index|Component) template[ \\t]+`([^`]+)`[ \\t]*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNIX_LINES);
    private static final Pattern FENCED = Pattern.compile("^```[a-z]*[ \\t]*\\n(.*?)^```",
            Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNIX_LINES);
    private static final Pattern INDEX_REQUEST = Pattern.compile("^(PUT|POST)\\s+/?_index_template/([^\\s?{]+)[^\\n{]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern COMPONENT_REQUEST = Pattern.compile(
            "(PUT|POST|GET)\\s+/?_component_template/?([^\\s?{]*)[^\\n{]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private IndexTemplates() {
        // Static only.
    }

    /// @param body A template as a GET returns it.
    /// @return It kept to what a PUT of an index template takes. Asked for in testing: the user pasted GET output,
    /// and the request to commit carried created_date_millis, which Elasticsearch refuses.
    public static Object asPut(final Object body) {
        return asPut(body, INDEX_TEMPLATE_FIELDS);
    }

    /// @param body   A template as a GET returns it (anything else is given back as it is).
    /// @param fields The fields a PUT takes, e.g. [#COMPONENT_TEMPLATE_FIELDS].
    /// @return It kept to those fields, its template part to what a PUT takes, and a lifecycle without its rollover.
    public static Object asPut(final Object body, final List<String> fields) {
        final Map<String, Object> given = JsonValues.map(body);
        if (given == null) {
            return body;
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        given.forEach((key, value) -> {
            if (fields.contains(key)) {
                out.put(key, value);
            }
        });
        final Map<String, Object> template = JsonValues.map(out.get("template"));
        if (template != null) {
            final Map<String, Object> kept = new LinkedHashMap<>();
            template.forEach((key, value) -> {
                if (TEMPLATE_FIELDS.contains(key)) {
                    kept.put(key, value);
                }
            });
            final Map<String, Object> lifecycle = JsonValues.map(kept.get("lifecycle"));
            if (lifecycle != null) {
                // Only rollover: a lifecycle's own settings grow from version to version.
                final Map<String, Object> shorter = new LinkedHashMap<>(lifecycle);
                shorter.remove("rollover");
                kept.put("lifecycle", shorter);
            }
            out.put("template", kept);
        }
        return out;
    }

    /// @param text The output of Stroom's elastic_index_templates tool, pasted (asked for by the Stroom agent), or
    ///             anything else.
    /// @return Each template in it: its kind (`index` or `component`), name and body (null if Stroom didn't read
    /// it); none for anything else.
    public static List<StroomTemplate> stroomToolTemplates(final Object text) {
        final List<StroomTemplate> found = new ArrayList<>();
        if (!(text instanceof final String given)) {
            return found;
        }
        final List<int[]> spans = new ArrayList<>();
        final List<String[]> names = new ArrayList<>();
        final Matcher again = STROOM_HEADING.matcher(given);
        while (again.find()) {
            spans.add(new int[]{again.start(), again.end()});
            names.add(new String[]{again.group(1), again.group(2)});
        }
        for (int n = 0; n < spans.size(); n++) {
            final int end = n + 1 < spans.size()
                    ? spans.get(n + 1)[0]
                    : given.length();
            final String section = given.substring(spans.get(n)[1], end);
            final Matcher block = FENCED.matcher(section);
            final String kind = names.get(n)[0].toLowerCase(Locale.ROOT);
            final String name = names.get(n)[1];
            found.add(new StroomTemplate(kind, name, block.find()
                    ? json(block.group(1), kind + " template '" + name + "'")
                    : null));
        }
        return found;
    }

    /// An Elasticsearch index template as the user pastes it: Dev Tools `PUT _index_template/name {...}`, the body,
    /// GET _index_template output, or Stroom's elastic_index_templates output; or an existing index's mapping (GET
    /// `<index>/_mapping`, or `{mappings: ...}`), which has no index_patterns, so which indices it covers is not
    /// checked.
    ///
    /// @param given The text pasted.
    /// @return The template's name (null if the text doesn't say) and body.
    /// @throws IllegalArgumentException if it isn't one, saying why.
    public static NamedTemplate parseTemplate(final String given) {
        String text = PyText.strip(given);
        String name = null;
        // The request line, then the body on the next line or the same one (seen in Ask Stroom AI: an agent gave
        // the server's own dev_tools on one line, and it was refused as not JSON).
        final Matcher request = INDEX_REQUEST.matcher(text);
        if (request.lookingAt()) {
            name = request.group(2);
            text = text.substring(request.end());
        }
        final List<StroomTemplate> fromStroom = stroomToolTemplates(text).stream()
                .filter(template -> "index".equals(template.kind()))
                .toList();
        Object body;
        if (!fromStroom.isEmpty()) {
            name = fromStroom.getFirst().name();
            body = fromStroom.getFirst().body();
            if (body == null) {
                throw new IllegalArgumentException("Stroom didn't read index template '" + name + "' (its output "
                                                   + "says why): paste GET _index_template/" + name + " instead");
            }
        } else {
            body = json(text, "index template");
        }
        Map<String, Object> map = JsonValues.map(body);
        if (map != null && map.containsKey("index_templates")) {
            final List<Object> entries = JsonValues.listOrEmpty(map.get("index_templates"));
            final Map<String, Object> entry = entries.isEmpty()
                    ? new LinkedHashMap<>()
                    : JsonValues.mapOrEmpty(entries.getFirst());
            if (entry.containsKey("name")) {
                name = entry.get("name") == null
                        ? null
                        : String.valueOf(entry.get("name"));
            }
            body = entry.containsKey("index_template")
                    ? entry.get("index_template")
                    : new LinkedHashMap<>();
            map = JsonValues.map(body);
        }
        if (map != null && !map.containsKey("index_patterns")) {
            // A mapping: GET <index>/_mapping ({index: {mappings}}), {mappings: ...}, or the mappings themselves.
            if (map.size() == 1 && map.values().iterator().next() instanceof final Map<?, ?> only
                && only.containsKey("mappings")) {
                name = map.keySet().iterator().next();
                map = JsonValues.map(only);
            }
            final Object mappings = map.containsKey("mappings")
                    ? map.get("mappings")
                    : map.containsKey("properties")
                            ? map
                            : null;
            if (mappings instanceof Map<?, ?>) {
                final Map<String, Object> template = new LinkedHashMap<>();
                template.put("mappings", mappings);
                final Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put("template", template);
                return new NamedTemplate(name, wrapped);
            }
        }
        if (map == null || !map.containsKey("index_patterns")) {
            throw new IllegalArgumentException("Expected an Elasticsearch index template (index_patterns and "
                                               + "template.mappings), or an index's mapping (GET <index>/_mapping)");
        }
        return new NamedTemplate(name, JsonValues.map(asPut(map)));
    }

    /// The index template as a Dev Tools request, laid out to read: an object of plain values (a field's mapping, a
    /// setting) on one line, others indented. Indented throughout, a 50-field template ran to 150 lines and 10,000
    /// characters, and the reply that showed it to the user for review was too long for the client to show.
    ///
    /// @param name The template's name.
    /// @param body Its body.
    /// @return The request.
    public static String devTools(final String name, final Map<String, Object> body) {
        return "PUT _index_template/" + name + "\n" + render(body, 0);
    }

    private static String render(final Object node, final int depth) {
        final String flat = PyJson.dumps(node);
        if (!(node instanceof Map<?, ?> || node instanceof List<?>) || flat.codePointCount(0, flat.length()) <= 90) {
            return flat;
        }
        final String pad = "  ".repeat(depth);
        final String inner = "  ".repeat(depth + 1);
        final List<String> items = new ArrayList<>();
        if (node instanceof final Map<?, ?> map) {
            map.forEach((key, value) -> items.add(inner + PyJson.dumps(String.valueOf(key)) + ": "
                                                  + render(value, depth + 1)));
            return "{\n" + String.join(",\n", items) + "\n" + pad + "}";
        }
        for (final Object value : (List<?>) node) {
            items.add(inner + render(value, depth + 1));
        }
        return "[\n" + String.join(",\n", items) + "\n" + pad + "]";
    }

    /// Component templates as the user pastes them, by name: Dev Tools `PUT _component_template/name {...}`, or GET
    /// _component_template output; several in one text (Kibana's responses to several requests, each after its `#`
    /// line), Stroom's elastic_index_templates output, or already JSON. Each needs its name, to be matched to
    /// composed_of. Seen in testing: re-sent by the agent, the bodies came back as a GET response inside a GET
    /// response, and as an escaped JSON string.
    ///
    /// @param texts The texts (or JSON values) given.
    /// @return Each component template's body (as a PUT takes it), by name.
    /// @throws IllegalArgumentException if one can't be read, or has no name, saying why.
    public static Map<String, Map<String, Object>> parseComponentTemplates(final List<?> texts) {
        final Map<String, Map<String, Object>> found = new LinkedHashMap<>();
        for (final Object item : texts) {
            addComponent(found, item, null);
        }
        return found;
    }

    private static void addComponent(final Map<String, Map<String, Object>> found,
                                     final Object body,
                                     final String name) {
        final Map<String, Object> map = JsonValues.map(body);
        if (body instanceof final String text) {
            readComponents(found, text);
        } else if (body instanceof final List<?> list) {
            for (final Object item : list) {
                addComponent(found, item, null);
            }
        } else if (map != null && map.containsKey("component_templates")) {
            for (final Object entry : JsonValues.listOrEmpty(map.get("component_templates"))) {
                addComponent(found, entry, null);
            }
        } else if (map != null && map.containsKey("component_template") && JsonValues.truthy(map.get("name"))) {
            found.put(String.valueOf(map.get("name")), JsonValues.map(asPut(
                    JsonValues.mapOrEmpty(map.get("component_template")), COMPONENT_TEMPLATE_FIELDS)));
        } else if (map != null && JsonValues.truthy(name)) {
            found.put(name, JsonValues.map(asPut(map, COMPONENT_TEMPLATE_FIELDS)));
        } else {
            throw new IllegalArgumentException("Give each component template with its name, as a Dev Tools request "
                                               + "(PUT _component_template/<name> {...}) or GET "
                                               + "_component_template/<name> output, so it can be matched to the "
                                               + "index template's composed_of");
        }
    }

    private static void readComponents(final Map<String, Map<String, Object>> found, final String given) {
        final List<StroomTemplate> fromStroom = stroomToolTemplates(given);
        if (!fromStroom.isEmpty()) {
            // A component Stroom didn't read isn't given: asked for alone, as for any other.
            for (final StroomTemplate template : fromStroom) {
                if ("component".equals(template.kind()) && template.body() != null) {
                    addComponent(found, template.body(), template.name());
                }
            }
            return;
        }
        final String text = PyText.strip(given);
        int at = 0;
        String name = null;
        while (at < text.length()) {
            final Matcher request = COMPONENT_REQUEST.matcher(text).region(at, text.length());
            if (PyText.isSpace(text.charAt(at))) {
                at++;
            } else if (text.charAt(at) == '#') {
                // Kibana's line before each response: '# GET ... 200 OK'.
                final int newline = text.indexOf('\n', at);
                at = newline + 1 == 0
                        ? text.length()
                        : newline + 1;
            } else if (request.lookingAt()) {
                name = "GET".equalsIgnoreCase(request.group(1)) || request.group(2).isEmpty()
                        ? null
                        : request.group(2);
                at = request.end();
            } else {
                final PyJson.Decoded decoded;
                try {
                    decoded = PyJson.rawDecode(text, at);
                } catch (final JsonDecodeException e) {
                    throw new IllegalArgumentException("The component template is not valid JSON: "
                                                       + e.getMessage(), e);
                }
                at = decoded.end();
                addComponent(found, decoded.value(), name);
                name = null;
            }
        }
    }

    // A text as JSON, or why it isn't.
    private static Object json(final String text, final String what) {
        try {
            return PyJson.loads(text);
        } catch (final JsonDecodeException e) {
            throw new IllegalArgumentException("The " + what + " is not valid JSON: " + e.getMessage(), e);
        }
    }


    // --------------------------------------------------------------------------------


    /// A template in the output of Stroom's elastic_index_templates tool.
    ///
    /// @param kind `index` or `component`.
    /// @param name Its name.
    /// @param body Its body as Elasticsearch returns it, or null if Stroom didn't read it.
    public record StroomTemplate(String kind, String name, Object body) {

    }

    /// An index template read from what the user pasted.
    ///
    /// @param name Its name, or null if the text doesn't say.
    /// @param body Its body.
    public record NamedTemplate(String name, Map<String, Object> body) {

    }
}
