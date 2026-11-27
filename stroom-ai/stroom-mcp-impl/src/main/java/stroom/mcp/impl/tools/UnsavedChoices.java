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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// What the user chose in build_translation_xslt's forms for an XSLT (its uuid, or build/name), kept as soon as they
/// chose it, until the XSLT is saved with it ([TranslationBuilds#rememberAgreed]). Seen in testing: a domain split
/// answered, then a later form failed, so the XSLT wasn't saved and the answer was lost; each call after asked
/// again, and the user could never get past it.
///
/// Each XSLT's choices are kept in the session's workspace (the chat's, or the MCP session's), under
/// `/outputs/choices/`, as SentMappings keeps the mappings sent. A session without a workspace keeps nothing, and its
/// user is asked again.
final class UnsavedChoices {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UnsavedChoices.class);

    /// The folder the choices are kept in.
    static final String FOLDER = "/outputs/choices/";

    private UnsavedChoices() {
        // Static only.
    }

    /// Keeps choices for the target, with any kept for it before (a later choice for the same key replaces one).
    ///
    /// @param context The call's context.
    /// @param target  The XSLT's uuid, or `build/name`; nothing is kept for "".
    /// @param block   What was chosen, e.g. [TranslationBuilds#DOMAIN_CHOSEN].
    /// @param choices What was chosen, by its key.
    static void remember(final ToolCallContext context,
                         final String target,
                         final String block,
                         final Map<String, String> choices) {
        if (NullSafe.isBlankString(target) || NullSafe.isEmptyMap(choices)) {
            return;
        }
        final Optional<SessionWorkspace> workspace = context.workspace();
        if (workspace.isEmpty()) {
            return;
        }
        try {
            final Map<String, Map<String, String>> all = new LinkedHashMap<>(read(workspace.get(), target));
            final Map<String, String> kept = new LinkedHashMap<>(NullSafe.map(all.get(block)));
            kept.putAll(choices);
            all.put(block, kept);
            workspace.get().write(path(target), JsonUtil.writeValueAsString(all, false), null);
        } catch (final RuntimeException e) {
            // A convenience: the user is asked again.
            LOGGER.debug(() -> LogUtil.message("Couldn't keep the choices made for {}: {}", target, e.getMessage()),
                    e);
        }
    }

    /// @param context The call's context.
    /// @param target  The XSLT's uuid, or `build/name`.
    /// @param block   What was chosen, e.g. [TranslationBuilds#DOMAIN_CHOSEN].
    /// @return What the user chose for it in this session, by its key; nothing if none was kept.
    static Map<String, String> recalled(final ToolCallContext context, final String target, final String block) {
        if (NullSafe.isBlankString(target)) {
            return Map.of();
        }
        return context.workspace()
                .map(workspace -> NullSafe.map(read(workspace, target).get(block)))
                .orElse(Map.of());
    }

    /// @param target A target.
    /// @return Its file in the workspace, by the target's digest.
    static WorkspacePath path(final String target) {
        return WorkspacePath.parse(FOLDER + XsltCode.sha256(target).substring(0, 16) + ".json");
    }

    // The target's choices, by block, or none if none were kept or they can't be read.
    private static Map<String, Map<String, String>> read(final SessionWorkspace workspace, final String target) {
        try {
            final WorkspacePath path = path(target);
            if (workspace.find(path).isEmpty()) {
                return Map.of();
            }
            // Written as one line of JSON.
            final String text = String.join("\n", workspace.readLines(path, 0, Integer.MAX_VALUE));
            final Map<String, Map<String, String>> all = new LinkedHashMap<>();
            if (JsonUtil.readValue(text, Map.class) instanceof final Map<?, ?> blocks) {
                blocks.forEach((block, choices) -> {
                    if (choices instanceof final Map<?, ?> map) {
                        final Map<String, String> kept = new LinkedHashMap<>();
                        map.forEach((key, choice) -> kept.put(String.valueOf(key), String.valueOf(choice)));
                        all.put(String.valueOf(block), kept);
                    }
                });
            }
            return all;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No choices kept for {}: {}", target, e.getMessage()), e);
            return Map.of();
        }
    }
}
