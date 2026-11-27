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

package stroom.mcp.impl;

import stroom.util.json.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The mapping a draft_translation_mapping result drafted, as a client outside Stroom gets it: in the result, or,
/// for a draft too large to give whole, read with jq from the workspace file it was saved to, an entry a line as
/// the agent reads it (a read cuts a line past 2,000 characters).
public final class DraftedMappings {

    private DraftedMappings() {
        // Static only.
    }

    /// @param client The client the draft was made by.
    /// @param draft  A draft_translation_mapping result.
    /// @return Its mapping.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> of(final McpTestClient client, final Map<String, Object> draft) {
        if (draft.get("mapping") instanceof final Map<?, ?> mapping) {
            return (Map<String, Object>) mapping;
        }
        assertThat(draft).as("a draft with no mapping says where it was saved, and outlines it")
                .containsKeys("mapping_saved", "mapping_outline");
        final Object path = draft.get("mapping_saved");
        final Map<String, Object> mapping = new LinkedHashMap<>();
        for (final Object key : JsonUtil.readValue(jq(client, path, "keys"), List.class)) {
            final String at = ".[\"" + key + "\"]";
            if ("events".equals(key)) {
                final int rules = Integer.parseInt(jq(client, path, at + " | length").strip());
                final List<Object> events = new ArrayList<>();
                for (int i = 0; i < rules; i++) {
                    final String rule = at + "[" + i + "]";
                    final String withoutFields = jq(client, path, rule + " | del(.fields)");
                    final Map<String, Object> event = new LinkedHashMap<>(JsonUtil.readValue(withoutFields,
                            Map.class));
                    event.put("fields", jqItems(client, path, rule + ".fields"));
                    events.add(event);
                }
                mapping.put("events", events);
            } else if ("\"array\"".equals(jq(client, path, at + " | type").strip())) {
                mapping.put((String) key, jqItems(client, path, at));
            } else {
                mapping.put((String) key, JsonUtil.readValue(jq(client, path, at), Object.class));
            }
        }
        return mapping;
    }

    // The items of a list in a workspace JSON file, one a line.
    private static List<Object> jqItems(final McpTestClient client, final Object path, final String list) {
        final String text = jq(client, path, list + "[]");
        return "No results.".equals(text)
                ? List.of()
                : text.lines().map(line -> JsonUtil.readValue(line, Object.class)).toList();
    }

    private static String jq(final McpTestClient client, final Object path, final String query) {
        return McpTestClient.text(client.call("jq", Map.of("path", path, "query", query)));
    }
}
