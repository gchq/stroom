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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// EventSource/System/Name for a stream of several formats, each read by its own rules (a variant). Seen in testing
/// (chat 144): a feed of five products, the user asked to name the system "from the log documentation", meaning each
/// product's; the draft held one System/Name for every event and the agent put the reference's title there. So with
/// several variants and no one name given, each rule gets its own, to ask the user for; and `system_name` may give a
/// name per variant.
final class SystemNames {

    // What a rule's System/Name says until the user names it.
    private static final String PLACEHOLDER = "TODO system name";

    private SystemNames() {
        // Static only.
    }

    /// @param given The `system_name` argument: a name, or a JSON object of each variant's name.
    /// @return The name for every event, or null when none, or one per variant, was given.
    static String single(final Object given) {
        final String text = given == null
                ? null
                : ToolText.strip(String.valueOf(given));
        return text == null || text.isEmpty() || given instanceof Map || text.startsWith("{")
                ? null
                : text;
    }

    /// Gives each variant's rules their own System/Name, where the stream has several and no one name was given.
    ///
    /// @param mapping      The draft's mapping.
    /// @param variantField The field the variants are told apart by, or null for a stream of one format.
    /// @param given        The `system_name` argument.
    /// @param notes        The draft's notes, added to.
    @SuppressWarnings("unchecked")
    static void apply(final Map<String, Object> mapping,
                      final String variantField,
                      final Object given,
                      final List<Object> notes) {
        if (variantField == null || single(given) != null) {
            return;
        }
        final Map<String, String> byVariant = byVariant(given);
        final List<Map<String, Object>> rules = JsonValues.maps(mapping.get("events"));
        final Set<String> variants = new LinkedHashSet<>();
        rules.forEach(rule -> {
            final String variant = variantOf(rule, variantField);
            if (variant != null) {
                variants.add(variant);
            }
        });
        if (variants.size() < 2) {
            return;
        }
        final List<Object> common = new ArrayList<>(JsonValues.listOrEmpty(mapping.get("common")));
        common.removeIf(entry -> entry instanceof final Map<?, ?> map && "EventSource/System/Name".equals(map.get(
                "path")));
        mapping.put("common", common);
        for (final Map<String, Object> rule : rules) {
            final String variant = variantOf(rule, variantField);
            final String name = variant == null
                    ? PLACEHOLDER
                    : byVariant.getOrDefault(variant, PLACEHOLDER + " (" + variant + ")");
            final List<Object> fields = new ArrayList<>(JsonValues.listOrEmpty(rule.get("fields")));
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", "EventSource/System/Name");
            entry.put("value", name);
            fields.addFirst(entry);
            rule.put("fields", fields);
        }
        final List<String> unnamed = variants.stream().filter(variant -> !byVariant.containsKey(variant)).toList();
        if (!unnamed.isEmpty()) {
            notes.add("The stream holds " + variants.size() + " formats (" + String.join(", ", variants) + "), each "
                      + "read by its own rules, so each rule has its own EventSource/System/Name. Ask the user "
                      + "whether they are one system (give system_name once) or one per product: each variant's, "
                      + "from the user or the vendor's documentation (record_source_notes), as "
                      + "system_name={\"<variant>\": \"<system>\"}. Unnamed yet: " + String.join(", ", unnamed)
                      + ".");
        }
    }

    // Each variant's name, as a JSON object gives them; none when not an object.
    private static Map<String, String> byVariant(final Object given) {
        Object value = given;
        if (value instanceof final String text && ToolText.strip(text).startsWith("{")) {
            try {
                value = ToolJson.loads(text);
            } catch (final RuntimeException e) {
                throw new ToolRefusal("system_name is a name, or a JSON object of each variant's name, e.g. "
                                      + "{\"cef\": \"SecureGate\", \"syslog\": \"Atlas BackupD\"}");
            }
        }
        final Map<String, String> out = new LinkedHashMap<>();
        if (value instanceof final Map<?, ?> map) {
            map.forEach((variant, name) -> {
                if (name != null && !ToolText.isBlank(String.valueOf(name))) {
                    out.put(String.valueOf(variant), ToolText.strip(String.valueOf(name)));
                }
            });
        }
        return out;
    }

    // The variant a rule reads, from its condition on the variant field.
    private static String variantOf(final Map<String, Object> rule, final String variantField) {
        for (final Map<String, Object> condition : JsonValues.maps(rule.get("when"))) {
            if (variantField.equals(condition.get("field")) && condition.get("equals") != null) {
                return String.valueOf(condition.get("equals"));
            }
        }
        return null;
    }
}
