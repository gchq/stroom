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
import stroom.util.json.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A build_translation_xslt result too large for a client outside Stroom to take whole: each rule's Data entries
/// (`<action element>/Data[<field>]`) counted in its `events` summary rather than listed. Seen in eval cases 35 and 36
/// over Claude Code: results of about 50 KB saved to files by the client, most of them the rules' Data (in the Pega
/// sample, 19,000 of 32,000 characters for 13 rules). The mapping, kept by the server, still lists them.
final class BuiltRulesTrim {

    private static final String EVENTS = "events";
    private static final String DATA = "/Data[";

    private BuiltRulesTrim() {
        // Static only.
    }

    /// Counts each rule's Data in the result's `events` when the caller is a client outside Stroom and the result is
    /// past the limit; Stroom AI's own chat, which keeps a large result in its workspace itself, gets it whole.
    ///
    /// @param caller Who called the tool.
    /// @param result The build's result, changed in place.
    /// @param limit  The most characters a result is given whole in ([DraftOutline#WHOLE_RESULT_CHARS]).
    static void trimIfLarge(final ToolCaller caller, final Map<String, Object> result, final int limit) {
        if (caller.kind() != ToolCaller.Kind.EXTERNAL_CLIENT || !(result.get(EVENTS) instanceof List<?>)
            || JsonUtil.writeValueAsString(result, false).length() <= limit) {
            return;
        }
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(result.get(EVENTS))) {
            final Map<String, Object> said = new LinkedHashMap<>(rule);
            final List<String> fields = new ArrayList<>();
            int data = 0;
            for (final Object field : JsonValues.listOrEmpty(rule.get("fields"))) {
                final String path = ToolText.str(field);
                if (path.contains(DATA)) {
                    data++;
                } else {
                    fields.add(path);
                }
            }
            said.put("fields", fields);
            said.put("data", data);
            rules.add(said);
        }
        result.put(EVENTS, rules);
        result.put("events_note", "Each rule's Data are counted here, not listed (this result is too large to "
                                  + "show whole): the mapping lists them, the fields each rule carries as Data of "
                                  + "its action element.");
    }
}
