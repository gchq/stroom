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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// A draft too large for a client to take whole: saved to the workspace, its result giving the mapping's outline.
/// Seen in eval cases 35, 36 and 56 on Haiku over Claude Code: drafts of 74,000 to 168,000 characters, past the
/// client's limit, so the agent never saw the draft (nor its hint that the server keeps it) and wrote a mapping of
/// its own, losing every event type's Description and the vendor's code.
final class DraftOutline {

    /// The most characters a draft's result is given whole in: Claude Code takes about 25,000 tokens of a tool's
    /// result, and saves a result past some 40,000 characters to a file, showing only its start.
    static final int WHOLE_RESULT_CHARS = 40_000;
    /// The folder a draft too large to give whole is saved in.
    static final String FOLDER = "/outputs/drafts/";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DraftOutline.class);

    // The most characters of a fixed value (a Description's text) said in the outline.
    private static final int VALUE_CHARS = 80;
    private static final Pattern PLAIN = Pattern.compile("[^A-Za-z0-9._-]");
    private static final String MAPPING = "mapping";
    private static final String SAVED = "mapping_saved";
    private static final String OUTLINE = "mapping_outline";
    private static final String PLACEMENT = "data_placement";
    private static final String EVENTS = "events";
    private static final String PATH = "path";
    private static final String FIELD = "field";
    private static final String VALUE = "value";
    private static final String DATA_NAME = "data_name";
    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final String DESCRIPTION = "EventDetail/Description";

    private DraftOutline() {
        // Static only.
    }

    /// Replaces a draft result's mapping by its outline when the result is past the limit. The mapping is saved
    /// whole to the workspace (`mapping_saved`, pretty-printed so it reads a part at a time), and `mapping_outline`
    /// gives its input, its common entries and, per rule, its event type's TypeId, how its records are recognised,
    /// its Description, its vendor-code Data, the other paths it writes and how many other Data it carries;
    /// `data_placement` lists each action element's elements once ([DataPlacements#compact]). An
    /// outline itself past the limit says less of each rule (no other paths), then lists only the rules that fit.
    /// The server keeps the draft for the session either way, so the first save sends only what changes. Given
    /// whole to Stroom AI's own chat (which keeps a large result in its workspace itself), when there is no workspace,
    /// or when the file can't be written.
    ///
    /// @param caller    Who called the tool.
    /// @param result    The draft's result, changed in place.
    /// @param workspace The session's workspace, if it has one.
    /// @param build     The build, or null.
    /// @param limit     The most characters a result is given whole in.
    static void outlineIfLarge(final ToolCaller caller,
                               final Map<String, Object> result,
                               final Optional<SessionWorkspace> workspace,
                               final String build,
                               final int limit) {
        final Map<String, Object> mapping = JsonValues.map(result.get(MAPPING));
        if (caller.kind() != ToolCaller.Kind.EXTERNAL_CLIENT || mapping == null || workspace.isEmpty()
            || length(result) <= limit) {
            return;
        }
        final String path = savedPath(build);
        try {
            workspace.get().write(WorkspacePath.parse(path), JsonUtil.writeValueAsString(mapping, true), null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The draft wasn't saved to {}: {}", path, e.getMessage()), e);
            return;
        }
        result.remove(MAPPING);
        result.put(SAVED, path);
        result.put("hint", "The draft is too large to show whole here, so mapping_outline outlines it: each event "
                           + "type's rule with its TypeId, how its records are recognised, its Description, the "
                           + "vendor's code it writes as Data and the other paths it writes. It is saved whole at "
                           + path + ": read a rule's entries with jq (path=" + path + ", query='.events[] | "
                           + "select(.name == \"<rule>\") | .fields[]', one a line), or find its lines with grep "
                           + "and read them with read_file. The server keeps the draft for this "
                           + "session, so build_translation_xslt build= (name=, or group= for a source type group) "
                           + "changes= sends only the rules and entries you change (changes={} builds it as "
                           + "drafted). Never write the mapping out yourself: its Descriptions and vendor-code Data "
                           + "would be lost. " + ToolText.str(result.getOrDefault("hint", "")));
        result.put(OUTLINE, outline(mapping, true));
        if (result.get(PLACEMENT) instanceof final Map<?, ?> placement) {
            result.put(PLACEMENT, DataPlacements.compact(JsonValues.map(placement)));
        }
        if (length(result) > limit) {
            result.put(OUTLINE, outline(mapping, false));
        }
        fitRules(result, limit);
    }

    /// @param build The build, or null.
    /// @return Where a draft too large to give whole is saved for it.
    static String savedPath(final String build) {
        return FOLDER + PLAIN.matcher(build == null
                ? "draft"
                : build).replaceAll("_") + ".json";
    }

    // The outline's rules cut to those that fit the limit, the rest counted: read from the saved file.
    private static void fitRules(final Map<String, Object> result, final int limit) {
        final Map<String, Object> outline = JsonValues.map(result.get(OUTLINE));
        final List<Map<String, Object>> rules = new ArrayList<>(JsonValues.maps(outline.get(EVENTS)));
        final int all = rules.size();
        while (length(result) > limit && rules.size() > 1) {
            rules.subList(Math.max(1, rules.size() - Math.max(1, rules.size() / 10)), rules.size()).clear();
            outline.put(EVENTS, rules);
            outline.put("rules_not_listed", (all - rules.size()) + " more rules after these: read them from "
                                            + result.get(SAVED));
        }
    }

    // The mapping's input, common entries, and each rule in brief.
    private static Map<String, Object> outline(final Map<String, Object> mapping, final boolean withPaths) {
        final Map<String, Object> outline = new LinkedHashMap<>();
        if (mapping.get("input") != null) {
            outline.put("input", mapping.get("input"));
        }
        outline.put("common", JsonValues.maps(mapping.get("common")).stream()
                .map(DraftOutline::entryWords)
                .toList());
        final String commonDescription = JsonValues.maps(mapping.get("common")).stream()
                .filter(entry -> DESCRIPTION.equals(entry.get(PATH)))
                .findFirst()
                .map(DraftOutline::sourceWords)
                .orElse(null);
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get(EVENTS))) {
            rules.add(ruleOutline(rule, commonDescription, withPaths));
        }
        outline.put(EVENTS, rules);
        return outline;
    }

    // One rule in brief: its name, TypeId, recognised by, Description, vendor-code Data, other paths and Data count.
    private static Map<String, Object> ruleOutline(final Map<String, Object> rule,
                                                   final String commonDescription,
                                                   final boolean withPaths) {
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("name", rule.get("name"));
        final List<Map<String, Object>> fields = JsonValues.maps(rule.get("fields"));
        fields.stream()
                .filter(field -> TYPE_ID.equals(field.get(PATH)))
                .findFirst()
                .ifPresent(field -> said.put("type_id", sourceWords(field)));
        final List<Condition> when = conditions(rule);
        if (JsonValues.truthy(rule.get("catch_all"))) {
            said.put("recognised_by", when.isEmpty()
                    ? List.of("uncatalogued records (no other rule recognises them)")
                    : List.of("uncatalogued records where " + String.join(" and ", words(when))));
        } else {
            said.put("recognised_by", when.isEmpty()
                    ? List.of("every record")
                    : words(when));
        }
        fields.stream()
                .filter(field -> DESCRIPTION.equals(field.get(PATH)))
                .findFirst()
                .ifPresent(field -> said.put("description", sourceWords(field)));
        if (!said.containsKey("description") && commonDescription != null) {
            said.put("description", "(common's) " + commonDescription);
        }
        final Set<String> codeFields = vendorCodeFields(when);
        final List<String> vendorCode = new ArrayList<>();
        final List<String> writes = new ArrayList<>();
        int otherData = JsonValues.listOrEmpty(rule.get("data")).size();
        for (final Map<String, Object> field : fields) {
            final String path = ToolText.str(field.get(PATH));
            if (JsonValues.truthy(field.get(DATA_NAME))) {
                if (codeFields.contains(ToolText.str(field.get(FIELD)))) {
                    vendorCode.add(field.get(DATA_NAME) + " <- " + field.get(FIELD));
                } else {
                    otherData++;
                }
            } else if (!TYPE_ID.equals(path) && !DESCRIPTION.equals(path)) {
                writes.add(path + " " + sourceWords(field));
            }
        }
        if (!vendorCode.isEmpty()) {
            said.put("vendor_code_data", vendorCode);
        }
        if (withPaths) {
            said.put("writes", writes);
        }
        said.put("other_data", otherData);
        if (JsonValues.truthy(rule.get("catch_all"))) {
            said.put("catch_all", true);
        }
        return said;
    }

    // A rule's conditions; one that doesn't read as a condition is left out (a draft's always do).
    private static List<Condition> conditions(final Map<String, Object> rule) {
        final List<Condition> conditions = new ArrayList<>();
        for (final Map<String, Object> json : JsonValues.maps(rule.get("when"))) {
            try {
                conditions.add(Condition.fromJson(json));
            } catch (final MappingValidationException e) {
                LOGGER.debug(() -> LogUtil.message("A condition not outlined: {}", e.getMessage()));
            }
        }
        return conditions;
    }

    private static List<String> words(final List<Condition> conditions) {
        return conditions.stream().map(MappingFacts::conditionWords).toList();
    }

    // The fields the conditions test for a code (equals, one_of; not a not): written as the vendor's code.
    private static Set<String> vendorCodeFields(final List<Condition> conditions) {
        final Set<String> fields = new LinkedHashSet<>();
        for (final Condition condition : conditions) {
            if (condition.field() != null && !condition.not()
                && (condition.equalTo() != null || condition.oneOf() != null)) {
                fields.add(condition.field());
            }
        }
        return fields;
    }

    // A common entry in brief: its path and where its value comes from.
    private static String entryWords(final Map<String, Object> entry) {
        final String dataName = entry.get(DATA_NAME) == null
                ? ""
                : " (Data " + entry.get(DATA_NAME) + ")";
        return entry.get(PATH) + dataName + " " + sourceWords(entry);
    }

    // Where an entry's value comes from: `= 'fixed'`, `<- field`, `choose from a, b` (for the agent to decide), else
    // `(xpath)` or `(computed)`.
    private static String sourceWords(final Map<String, Object> entry) {
        if (entry.get("choose_from") != null) {
            return "choose from " + String.join(", ", JsonValues.strings(entry.get("choose_from")))
                   + " (yours to decide)";
        }
        if (entry.get(FIELD) != null) {
            return "<- " + entry.get(FIELD);
        }
        if (entry.get(VALUE) != null) {
            final String value = ToolText.str(entry.get(VALUE));
            return "= '" + (value.length() > VALUE_CHARS
                    ? value.substring(0, VALUE_CHARS) + "..."
                    : value) + "'";
        }
        return entry.get("xpath") != null
                ? "(xpath)"
                : "(computed)";
    }

    private static int length(final Map<String, Object> result) {
        return JsonUtil.writeValueAsString(result, false).length();
    }
}
