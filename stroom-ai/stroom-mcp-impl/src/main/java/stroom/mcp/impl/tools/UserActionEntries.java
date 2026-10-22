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

import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolUserAction;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/// Steps that are the user's, as entries a client can show as buttons (asked for by the user: Stroom's Ask Stroom
/// AI shows each on the tool call's card, where a click is the user's consent; the model can neither make nor
/// click one). Ported from the Stroom MCP server's `utils/useractions.py` (`enable_filter`, `open_document` and
/// `actions`). The same steps stay in each result's text for other clients, and the tools never take a step as
/// done: they read Stroom's state (wait_for_processing, build_status).
///
/// A result gives them as `user_actions`, as the Stroom MCP server's did, and the chat is given them as
/// [ToolUserAction]s ([#withActions]), which it checks ([UserActions]) before showing them.
final class UserActionEntries {

    /// The key a result gives its user actions under.
    static final String USER_ACTIONS = "user_actions";
    // The only Elasticsearch requests offered: committing an index template, removing one (the old name's, after
    // rename_index), dropping one index or data stream. An index name never starts with _ - or + (so not _all),
    // and a wildcard or comma would name several.
    private static final Pattern ES_REQUEST = Pattern.compile(
            "PUT _index_template/[A-Za-z0-9][A-Za-z0-9._-]*\\n\\{.*}"
            + "|DELETE _index_template/[A-Za-z0-9][A-Za-z0-9._-]*"
            + "|DELETE (_data_stream/)?[a-z0-9][a-z0-9._-]*", Pattern.DOTALL);

    private UserActionEntries() {
        // Static only.
    }

    /// @param links    How the caller links to Stroom.
    /// @param filterId A processor filter.
    /// @param pipeline Its pipeline.
    /// @param what     What enabling it does, or "".
    /// @return A button that enables the filter.
    static Map<String, Object> enableFilter(final StroomLinks links,
                                            final int filterId,
                                            final DocRef pipeline,
                                            final String what) {
        return enableFilter(links, filterId, pipeline, what, List.of());
    }

    /// @param links    How the caller links to Stroom.
    /// @param filterId A processor filter.
    /// @param pipeline Its pipeline.
    /// @param what     What enabling it does, or "".
    /// @param after    What must come first: the ids of other actions, or conditions in words; none for nothing.
    /// @return A button that enables the filter, once what comes first is done.
    static Map<String, Object> enableFilter(final StroomLinks links,
                                            final int filterId,
                                            final DocRef pipeline,
                                            final String what,
                                            final List<String> after) {
        final Map<String, Object> doc = doc(pipeline, PipelineDoc.TYPE);
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "enable-" + filterId);
        entry.put("action", UserActions.ENABLE_PROCESSOR_FILTER);
        entry.put("filter_id", filterId);
        entry.put("doc", doc);
        entry.put("link", links.documentTarget(pipeline));
        entry.put("summary", "Enable filter " + filterId + " on pipeline '" + doc.get("name") + "'"
                             + (NullSafe.isNonBlankString(what)
                ? ": " + what
                : ""));
        if (!NullSafe.isEmptyCollection(after)) {
            entry.put("after", new ArrayList<>(after));
        }
        return entry;
    }

    /// An Elasticsearch request for the cluster admin to run, shown for copying: only of the shapes offered (a
    /// template committed or removed, one index or data stream dropped), so a malformed one is never a button.
    ///
    /// @param actionId The action's id, e.g. `drop-ecs-acme-v2`.
    /// @param request  The Dev Tools request, e.g. `DELETE ecs-acme-v2`.
    /// @param why      What it does, in words.
    /// @return The action, or null for a request not of a shape offered.
    static Map<String, Object> esRequest(final String actionId, final String request, final String why) {
        final String stripped = NullSafe.string(request).strip();
        if (!ES_REQUEST.matcher(stripped).matches()) {
            return null;
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", actionId);
        entry.put("action", UserActions.ELASTICSEARCH_REQUEST);
        entry.put("by", "cluster admin");
        entry.put("request", stripped);
        entry.put("summary", why);
        return entry;
    }

    /// @param links How the caller links to Stroom.
    /// @param ref   A document.
    /// @param why   What the user is to do there.
    /// @return A button that opens the document.
    static Map<String, Object> openDocument(final StroomLinks links, final DocRef ref, final String why) {
        final Map<String, Object> doc = doc(ref, NullSafe.nonBlankStringElse(ref.getType(), PipelineDoc.TYPE));
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "open-" + ref.getUuid().substring(0, Math.min(8, ref.getUuid().length())));
        entry.put("action", UserActions.OPEN_DOCUMENT);
        entry.put("doc", doc);
        entry.put("link", links.documentTarget(new DocRef(String.valueOf(doc.get("type")), ref.getUuid())));
        entry.put("summary", "Open " + doc.get("type") + " '" + doc.get("name") + "': " + why);
        return entry;
    }

    /// @param result  A tool's result.
    /// @param entries Its user actions, any of them null (left out).
    /// @return The result with `user_actions`, when there are any.
    static Map<String, Object> withEntries(final Map<String, Object> result, final List<Map<String, Object>> entries) {
        final List<Map<String, Object>> kept = entries.stream()
                .filter(Objects::nonNull)
                .toList();
        if (!kept.isEmpty()) {
            result.put(USER_ACTIONS, kept);
        }
        return result;
    }

    /// @param toolResult A tool's result.
    /// @param result     The JSON it was made from.
    /// @return The result with the user actions its JSON gives, for the chat to show as buttons.
    static ToolResult withActions(final ToolResult toolResult, final Map<String, Object> result) {
        ToolResult out = toolResult;
        if (result.get(USER_ACTIONS) instanceof final List<?> entries) {
            for (final Object item : entries) {
                if (item instanceof final Map<?, ?> entry) {
                    final Map<String, Object> arguments = new LinkedHashMap<>();
                    entry.forEach((key, value) -> {
                        if (!"id".equals(key) && !"action".equals(key)) {
                            arguments.put(String.valueOf(key), value);
                        }
                    });
                    out = out.withUserAction(new ToolUserAction(String.valueOf(entry.get("id")),
                            String.valueOf(entry.get("action")), arguments));
                }
            }
        }
        return out;
    }

    private static Map<String, Object> doc(final DocRef ref, final String type) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", type);
        doc.put("uuid", ref.getUuid());
        doc.put("name", NullSafe.nonBlankStringElse(ref.getName(), ref.getUuid()));
        return doc;
    }
}
