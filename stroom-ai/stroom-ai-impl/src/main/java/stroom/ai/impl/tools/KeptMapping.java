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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.sample.PyJson;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The mapping an XSLT was generated from, kept with the XSLT, as the Stroom MCP server keeps it.
///
/// An XSLT doc's description (its Documentation tab in Stroom) carries the translation mapping (or an indexing
/// field plan, or a CEF plan), so the documentation's Field mapping section can be regenerated later, a change can
/// start from the mapping rather than the XSLT, and hand edits to the XSLT since the mapping are detected. It is
/// kept hidden, as compact JSON in an HTML comment, after a visible note asking people to leave it be: shown as it
/// was (1,400 lines of JSON for one translation), it buried the tab's own text. The same goes for a build's
/// pending changes and a pipeline's agreed index template. Blocks written before (shown, between `---` markers)
/// are still read.
///
/// The blocks' markers are part of what is stored in documents, so they keep the server's name, `stroom-mcp`.
public final class KeptMapping {

    /// The note before the hidden blocks.
    public static final String NOTE = "*Below, hidden, is what stroom-mcp keeps for this document: the mapping it "
                                      + "is generated from, a build's pending changes, an agreed index template. "
                                      + "Please don't edit or delete it: change the document through the agent.*";
    /// The name of the block of a build's pending changes.
    public static final String PENDING = "pending changes";
    /// The kinds of mapping kept, in the order they are looked for.
    public static final List<String> KINDS = List.of("translation", "index", "cef");
    /// The name of the block of a pipeline's agreed index template.
    public static final String AGREED_TEMPLATE = "agreed index template";
    /// The mark a documentation doc's Field mapping section carries: `{digest}` is the digest of the mapping and the
    /// XSLT it was generated from.
    public static final String DOC_MARK = "<!-- stroom-mcp field-mapping {digest} -->";
    /// The kind of mapping an events pipeline's translation XSLT keeps.
    public static final String TRANSLATION = "translation";
    /// Where the stroom-mcp part of a description begins: its note, a hidden block, or a shown one written before.
    public static final Pattern SERVER_PART = Pattern.compile("(?:^|\\n)(?:" + Pattern.quote(NOTE.substring(0, 20))
                                                              + "|<!-- stroom-mcp [a-z ]+: kept by the server"
                                                              + "|--- stroom-mcp )");

    // Shown blocks, as written before: read, and replaced by hidden ones when next written.
    private static final Pattern SHOWN = Pattern.compile(
            "\\n*--- stroom-mcp ([a-z ]+?) (?:\\([^\\n]*\\) )?---\\n(.*?)\\n--- end of stroom-mcp [a-z ]+? ---\\n*",
            Pattern.DOTALL);
    private static final Pattern HIDDEN = Pattern.compile(
            "\\n*<!-- stroom-mcp ([a-z ]+): kept by the server, do not edit or delete\\n(.*?)\\n-->\\n*",
            Pattern.DOTALL);
    private static final Pattern DOC_MARK_PATTERN = Pattern.compile(
            "<!-- stroom-mcp field-mapping ([0-9a-f]{16}) -->");
    private static final Pattern NOTE_PATTERN = Pattern.compile("\\n*" + Pattern.quote(NOTE) + "\\n*");
    private static final Pattern DATA_SPLITTER_FIELD = Pattern.compile("(?:data\\[@name='([^']*)'\\]/)+@value");
    private static final Pattern DATA_NAME = Pattern.compile("data\\[@name='([^']*)'\\]");
    private static final Pattern JSON_FIELD = Pattern.compile("\\*\\[@key='[^']*'\\](?:/\\*\\[@key='[^']*'\\])*");
    private static final Pattern JSON_KEY = Pattern.compile("@key='([^']*)'");

    private KeptMapping() {
        // Static only.
    }

    /// @param description A document's description.
    /// @param name        A block's name, e.g. `translation mapping` or [#PENDING].
    /// @return The block's JSON object, hidden or (written before) shown; empty when there is none, or it doesn't
    /// parse, or isn't an object (a hand-edited description).
    public static Optional<Map<String, Object>> readBlock(final String description, final String name) {
        for (final Pattern pattern : List.of(HIDDEN, SHOWN)) {
            final Matcher matcher = pattern.matcher(NullSafe.string(description));
            while (matcher.find()) {
                if (matcher.group(1).equals(name)) {
                    return parseObject(matcher.group(2));
                }
            }
        }
        return Optional.empty();
    }

    /// @param description A document's description.
    /// @return The mapping it keeps, by kind (one of [#KINDS]), or empty when it holds no mapping block.
    public static Optional<Kept> readMapping(final String description) {
        for (final String kind : KINDS) {
            final Optional<Map<String, Object>> payload = readBlock(description, kind + " mapping");
            if (payload.isPresent()) {
                return Optional.of(new Kept(kind, payload.get()));
            }
        }
        return Optional.empty();
    }

    /// @param description A document's description.
    /// @return The build's pending changes it keeps, or empty if none.
    public static Optional<Map<String, Object>> pendingOf(final String description) {
        return readBlock(description, PENDING);
    }

    /// @param description A document's description.
    /// @param name        A block's name, or null for every block.
    /// @return The description without the named block, hidden or shown; with name null, without every block and
    /// the note: the document's own text (and its version history).
    public static String withoutBlock(final String description, final String name) {
        String text = drop(HIDDEN, NullSafe.string(description), name);
        text = drop(SHOWN, text, name);
        if (name == null || !(HIDDEN.matcher(text).find() || SHOWN.matcher(text).find())) {
            // The note stays only while a block it speaks of does.
            text = NOTE_PATTERN.matcher(text).replaceAll("\n\n");
        }
        return text.strip();
    }

    /// The description with a block (hidden, as compact JSON, written as the Stroom MCP server's
    /// `json.dumps(payload, ensure_ascii=False, separators=(',', ':'))` writes it) replacing any earlier one of its
    /// name, after the note. A comment can't hold '--': written '-\\u002d', which JSON reads back as '--' (it only
    /// occurs inside a string).
    ///
    /// @param description A document's description, or null.
    /// @param name        The block's name, e.g. [#PENDING].
    /// @param payload     What the block holds.
    /// @return The description with the block.
    public static String withBlock(final String description, final String name, final Object payload) {
        final String body = PyJson.dumpsCompact(payload).replace("--", "-\\u002d");
        final String block = "<!-- stroom-mcp " + name + ": kept by the server, do not edit or delete\n" + body
                             + "\n-->";
        final String text = NOTE_PATTERN.matcher(withoutBlock(description, name)).replaceAll("\n\n").strip();
        final Matcher at = SERVER_PART.matcher(text);
        final String head;
        final String tail;
        if (at.find()) {
            head = text.substring(0, at.start());
            tail = text.substring(at.start());
        } else {
            head = text;
            tail = "";
        }
        final List<String> parts = new ArrayList<>();
        for (final String part : List.of(head, NOTE, tail, block)) {
            if (!part.strip().isEmpty()) {
                parts.add(part.strip());
            }
        }
        return String.join("\n\n", parts);
    }

    /// The description with the mapping block replacing any earlier one (of any kind): the Stroom MCP server's
    /// `mappingstore.with_mapping`.
    ///
    /// @param description A document's description, or null.
    /// @param kind        The mapping's kind, one of [#KINDS].
    /// @param payload     The mapping block: for a translation, `schema_version` and `mapping` (the mapping's
    ///                    compact JSON).
    /// @return The description with the block.
    public static String withMapping(final String description, final String kind, final Map<String, Object> payload) {
        String text = NullSafe.string(description);
        for (final String other : KINDS) {
            if (!other.equals(kind)) {
                text = withoutBlock(text, other + " mapping");
            }
        }
        return withBlock(text, kind + " mapping", payload);
    }

    /// @param description A pipeline's description, or null.
    /// @param agreed      The Elasticsearch index template the user agreed for an indexing pipeline: what the
    ///                    cluster admin was asked to apply, for the index and cluster and the indexing XSLT it was
    ///                    agreed against.
    /// @return The description keeping it.
    public static String withAgreedTemplate(final String description, final Map<String, Object> agreed) {
        return withBlock(description, AGREED_TEMPLATE, agreed);
    }

    /// @param description A pipeline's description, or null.
    /// @return The index template agreed for it, or empty if none.
    public static Optional<Map<String, Object>> readAgreedTemplate(final String description) {
        return readBlock(description, AGREED_TEMPLATE);
    }

    /// @param markdown A documentation doc's text.
    /// @return The digest of the mapping and XSLT its Field mapping section was generated from (its
    /// [#DOC_MARK]), or empty if it has none.
    public static Optional<String> docDigest(final String markdown) {
        final Matcher matcher = DOC_MARK_PATTERN.matcher(NullSafe.string(markdown));
        return matcher.find()
                ? Optional.of(matcher.group(1))
                : Optional.empty();
    }

    /// The markdown with the `## heading` section's body replaced, or the section added before Output (else
    /// Conformance, Open items or Change log, else at the end): the Stroom MCP server's
    /// `mappingstore.replace_section`.
    ///
    /// @param markdown A documentation doc's text.
    /// @param heading  The section's heading, without `## `.
    /// @param body     Its new body.
    /// @param exact    True for a heading that is exactly this, not one starting with it.
    /// @return The markdown with the section.
    public static String replaceSection(final String markdown,
                                        final String heading,
                                        final String body,
                                        final boolean exact) {
        final String rest = exact
                ? "[ \\t]*"
                : "[^\\n]*";
        final Pattern pattern = Pattern.compile("(^## " + Pattern.quote(heading) + rest + "\\n)(.*?)(?=^## |\\z)",
                Pattern.MULTILINE | Pattern.DOTALL);
        final String section = "## " + heading + "\n\n" + body.strip() + "\n\n";
        final Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return markdown.substring(0, matcher.start()) + section + markdown.substring(matcher.end());
        }
        for (final String anchor : List.of("## Output", "## Conformance", "## Open items", "## Change log")) {
            final int at = markdown.indexOf(anchor);
            if (at >= 0) {
                return markdown.substring(0, at) + section + markdown.substring(at);
            }
        }
        return markdown.stripTrailing() + "\n\n" + section;
    }

    /// What `read_document` gives of an XSLT's kept mapping: by default a summary, as the whole mapping (1,400
    /// lines for one translation) was seen spilled by a client to a file, which the agent read in parts and then
    /// sent back whole to regenerate the XSLT.
    ///
    /// @param kept  The kept mapping.
    /// @param uuid  The XSLT's UUID.
    /// @param whole True for the whole mapping, to read.
    /// @return The mapping whole (`kind` and its payload), or its summary: for a translation, its `schema_version`,
    /// `input`, `rules` (a line a rule), `common_entries`, `extractions`, `style` and `how` to change it; for any
    /// other kind, its fields, with lists given as their sizes.
    public static Map<String, Object> summary(final Kept kept, final String uuid, final boolean whole) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("kind", kept.kind());
        if (whole) {
            summary.putAll(kept.payload());
            return summary;
        }
        if (TRANSLATION.equals(kept.kind())) {
            final Map<String, Object> body = JsonValues.mapOrEmpty(kept.payload().get("mapping"));
            summary.put("schema_version", kept.payload().get("schema_version"));
            summary.put("input", body.get("input"));
            summary.put("rules", eventKinds(body));
            summary.put("common_entries", JsonValues.listOrEmpty(body.get("common")).size());
            summary.put("extractions", JsonValues.listOrEmpty(body.get("extract")).size());
            summary.put("style", isTruthy(body.get("style"))
                    ? body.get("style")
                    : "the generator's current defaults");
            summary.put("how", "build_translation_xslt uuid='" + uuid + "' changes={...} changes it (only what "
                               + "changes); uuid='" + uuid + "' alone regenerates it as the generator writes it "
                               + "now. read_document mapping=true shows it whole, to read; never send it back "
                               + "whole.");
        } else {
            kept.payload().forEach((key, value) -> {
                if (value instanceof final List<?> items) {
                    summary.put(key, items.size());
                } else if (!(value instanceof Map)) {
                    summary.put(key, value);
                }
            });
        }
        return summary;
    }

    /// @param pending The pending changes block.
    /// @return Each pending change's text, oldest first.
    public static List<Object> pendingChanges(final Map<String, Object> pending) {
        return JsonValues.listOrEmpty(pending.get("entries")).stream()
                .map(entry -> JsonValues.mapOrEmpty(entry).get("change"))
                .toList();
    }

    /// @param mapping A translation mapping, as JSON-like values.
    /// @return What the events pipeline writes, a line a rule: its name, the action element, and when it applies,
    /// e.g. `logon: Authenticate (`action` = login)`.
    static List<String> eventKinds(final Map<String, Object> mapping) {
        final List<String> out = new ArrayList<>();
        final List<Object> common = JsonValues.listOrEmpty(mapping.get("common"));
        for (final Object item : JsonValues.listOrEmpty(mapping.get("events"))) {
            final Map<String, Object> rule = JsonValues.mapOrEmpty(item);
            final List<String> conditions = new ArrayList<>();
            JsonValues.listOrEmpty(rule.get("when"))
                    .forEach(condition -> conditions.add(conditionText(JsonValues.mapOrEmpty(condition))));
            final String when = conditions.isEmpty()
                    ? "any other record"
                    : String.join(" and ", conditions);
            final String name = NullSafe.string(stringOf(rule.get("name")));
            if (Boolean.TRUE.equals(rule.get("drop"))) {
                out.add(name + ": left untranslated (" + when + ")");
                continue;
            }
            final Set<String> actions = new TreeSet<>();
            final List<Object> fields = new ArrayList<>(common);
            fields.addAll(JsonValues.listOrEmpty(rule.get("fields")));
            for (final Object field : fields) {
                final String path = PyText.strip(NullSafe.string(stringOf(JsonValues.mapOrEmpty(field).get("path"))),
                        "/");
                final String[] parts = path.split("/");
                if (path.startsWith("EventDetail/") && parts.length > 1 && !"TypeId".equals(parts[1])
                    && !"Description".equals(parts[1])) {
                    actions.add(parts[1]);
                }
            }
            out.add(name + ": " + (actions.isEmpty()
                    ? "EventDetail"
                    : String.join(", ", actions)) + " (" + when + ")");
        }
        return out;
    }

    /// @param condition A rule's condition, as JSON-like values.
    /// @return It for a reader, e.g. `` `action` = login ``.
    public static String conditionText(final Map<String, Object> condition) {
        final String field = stringOf(condition.get("field"));
        final String source = (field != null
                ? "`" + field + "`"
                : "`" + readable(NullSafe.string(stringOf(condition.get("xpath")))) + "`")
                              + ("record".equals(condition.get("scope"))
                ? " (of the record)"
                : "");
        if (condition.get("equals") != null) {
            return source + " = " + stringOf(condition.get("equals"));
        }
        if (condition.get("one_of") != null) {
            return source + " in " + String.join(", ", JsonValues.listOrEmpty(condition.get("one_of")).stream()
                    .map(KeptMapping::stringOf)
                    .toList());
        }
        if (condition.get("matches") != null) {
            return source + " matches `" + stringOf(condition.get("matches")) + "`";
        }
        if (condition.get("in_dictionary") != null) {
            return source + " in dictionary `" + stringOf(condition.get("in_dictionary")) + "`";
        }
        return source + " " + (Boolean.TRUE.equals(condition.get("present"))
                ? "present"
                : "empty");
    }

    /// @param xpath An xpath, e.g. `normalize-space(data[@name='username']/@value)`.
    /// @return It with its field selectors written as the field names, for a reader, e.g.
    /// `normalize-space(username)`.
    public static String readable(final String xpath) {
        final String fields = replace(DATA_SPLITTER_FIELD, xpath, DATA_NAME, "/");
        return replace(JSON_FIELD, fields, JSON_KEY, ".");
    }

    // Each match of the pattern replaced by the names its parts select, joined.
    private static String replace(final Pattern pattern, final String text, final Pattern part, final String join) {
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final List<String> names = new ArrayList<>();
            final Matcher nameMatcher = part.matcher(matcher.group(0));
            while (nameMatcher.find()) {
                names.add(nameMatcher.group(1));
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.join(join, names)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String drop(final Pattern pattern, final String text, final String name) {
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(name == null || matcher.group(1).equals(name)
                    ? "\n\n"
                    : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Map<String, Object>> parseObject(final String json) {
        try {
            final Object parsed = JsonUtil.readValue(json, Object.class);
            return parsed instanceof Map
                    ? Optional.of((Map<String, Object>) parsed)
                    : Optional.empty();
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String stringOf(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }

    private static boolean isTruthy(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof final String string) {
            return !string.isEmpty();
        }
        return value != null;
    }


    // --------------------------------------------------------------------------------


    /// A mapping kept with a document.
    ///
    /// @param kind    Its kind, one of [#KINDS].
    /// @param payload The block's JSON: for a translation, `schema_version` and `mapping`.
    public record Kept(String kind, Map<String, Object> payload) {

    }
}
