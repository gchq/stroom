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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// The translation mapping as last sent for an XSLT (its uuid, or build/name), and the draft draft_translation_mapping
/// made, so a fix can send only its changes. Seen in testing: a first save from a 50 KB draft had to send it whole,
/// which the agent couldn't write out in one call.
///
/// Each is kept in the session's workspace (the chat's, or the MCP session's), under `/outputs/mappings/`, where
/// the agent can read it too; a call that finds none falls back to the mapping saved with the XSLT. A session
/// without a workspace remembers nothing.
final class SentMappings {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SentMappings.class);

    /// The folder the mappings are kept in.
    static final String FOLDER = "/outputs/mappings/";
    /// What the draft's target starts with: `draft:<build>` (or `draft:` for the draft made with no build).
    static final String DRAFT = "draft:";

    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._-]{0,99}");

    private SentMappings() {
        // Static only.
    }

    /// Keeps the mapping as sent for the target.
    ///
    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target; nothing is kept for "".
    /// @param mapping The mapping, as JSON maps and lists.
    static void remember(final ToolCallContext context, final String target, final Map<String, Object> mapping) {
        if (NullSafe.isBlankString(target) || mapping == null) {
            return;
        }
        final Optional<SessionWorkspace> workspace = context.workspace();
        if (workspace.isEmpty()) {
            return;
        }
        try {
            workspace.get().write(path(target), JsonUtil.writeValueAsString(mapping, false), null);
        } catch (final RuntimeException e) {
            // A convenience: changes= then falls back to the mapping saved with the XSLT.
            LOGGER.debug(() -> LogUtil.message("Couldn't keep the mapping sent for {}: {}", target, e.getMessage()), e);
        }
    }

    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target.
    /// @return The mapping last sent for it in this session, if any.
    @SuppressWarnings("unchecked")
    static Optional<Map<String, Object>> sent(final ToolCallContext context, final String target) {
        if (NullSafe.isBlankString(target)) {
            return Optional.empty();
        }
        final Optional<SessionWorkspace> workspace = context.workspace();
        if (workspace.isEmpty()) {
            return Optional.empty();
        }
        try {
            final WorkspacePath path = path(target);
            if (workspace.get().find(path).isEmpty()) {
                return Optional.empty();
            }
            // Written as one line of JSON.
            final String text = String.join("\n", workspace.get().readLines(path, 0, Integer.MAX_VALUE));
            return JsonUtil.readValue(text, Map.class) instanceof final Map<?, ?> mapping
                    ? Optional.of((Map<String, Object>) mapping)
                    : Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No mapping sent for {}: {}", target, e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// @param target A target.
    /// @return Its file in the workspace: the uuid or draft's build as it is, anything else by its digest.
    static WorkspacePath path(final String target) {
        final String name;
        if (target.startsWith(DRAFT)) {
            final String build = target.substring(DRAFT.length());
            name = build.isEmpty()
                    ? "draft"
                    : "draft-" + plain(build);
        } else {
            name = plain(target);
        }
        return WorkspacePath.parse(FOLDER + name + ".json");
    }

    private static String plain(final String text) {
        return PLAIN.matcher(text).matches()
                ? text
                : XsltCode.sha256(text).substring(0, 16);
    }
}
