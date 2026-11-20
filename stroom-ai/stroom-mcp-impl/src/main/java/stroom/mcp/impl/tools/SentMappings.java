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
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// The translation mapping as last sent for an XSLT (its uuid, or build/name), and the draft draft_translation_mapping
/// made, so a fix can send only its changes. Seen in testing: a first save from a 50 KB draft had to send it whole,
/// which the agent couldn't write out in one call.
///
/// Each is kept in the session's workspace (the chat's, or the MCP session's), under `/outputs/mappings/`, where
/// the agent can read it too; a call that finds none falls back to the mapping saved with the XSLT. A session
/// without a workspace remembers nothing.
///
/// Those of a build (all but the draft made with no build) are also kept with the build in the user's own workspace
/// ([ToolCallContext#userWorkspace()]), which outlasts the session, so a new session of the same user picks them up
/// (seen in eval case 36: the client gave up on a call and started a new session, and changes= right after a draft
/// was refused, the draft gone with the old session). Only that user reaches it. The newest wins: each session's
/// save replaces it, so one found there is the latest of all the user's sessions; [#earlierSession] says when one
/// came from an earlier session.
final class SentMappings {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SentMappings.class);

    /// The folder the mappings are kept in.
    static final String FOLDER = "/outputs/mappings/";
    /// What the draft's target starts with: `draft:<build>` (or `draft:` for the draft made with no build).
    static final String DRAFT = "draft:";

    // What a mapping kept with its build holds.
    private static final String TARGET = "target";
    private static final String SESSION = "session";
    private static final String SENT_MS = "sent_ms";
    private static final String UNSAVED = "unsaved";
    private static final String MAPPING = "mapping";

    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._-]{0,99}");

    private SentMappings() {
        // Static only.
    }

    /// Keeps the mapping as sent for the target, as saved (or a draft): no longer one with changes not saved.
    ///
    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target; nothing is kept for "".
    /// @param mapping The mapping, as JSON maps and lists.
    static void remember(final ToolCallContext context, final String target, final Map<String, Object> mapping) {
        remember(context, target, mapping, false);
    }

    /// Keeps the mapping as sent for the target by a call that saved nothing to it (its changes refused, or no
    /// save asked for): [#unsaved] gives it back until a save remembers the target's mapping again.
    ///
    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target; nothing is kept for "".
    /// @param mapping The mapping, as JSON maps and lists.
    static void rememberUnsaved(final ToolCallContext context,
                                final String target,
                                final Map<String, Object> mapping) {
        remember(context, target, mapping, true);
    }

    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target.
    /// @return The mapping last sent for it in this session by a call that saved nothing to it, if that is the
    /// latest: its changes are not in the saved XSLT.
    static Optional<Map<String, Object>> unsaved(final ToolCallContext context, final String target) {
        if (NullSafe.isBlankString(target)) {
            return Optional.empty();
        }
        final Optional<Kept> kept = keptWithTheBuild(context, target);
        if (kept.isPresent()) {
            return kept.get().unsaved()
                    ? Optional.of(kept.get().mapping())
                    : Optional.empty();
        }
        final boolean marked = context.workspace()
                .map(workspace -> {
                    try {
                        return workspace.find(unsavedPath(target)).isPresent();
                    } catch (final RuntimeException e) {
                        LOGGER.debug(() -> LogUtil.message("No unsaved mark read for {}: {}", target,
                                e.getMessage()), e);
                        return false;
                    }
                })
                .orElse(false);
        return marked
                ? sent(context, target)
                : Optional.empty();
    }

    private static void remember(final ToolCallContext context,
                                 final String target,
                                 final Map<String, Object> mapping,
                                 final boolean unsaved) {
        if (NullSafe.isBlankString(target) || mapping == null) {
            return;
        }
        keepWithTheBuild(context, target, mapping, unsaved);
        final Optional<SessionWorkspace> workspace = context.workspace();
        if (workspace.isEmpty()) {
            return;
        }
        try {
            workspace.get().write(path(target), JsonUtil.writeValueAsString(mapping, false), null);
            final WorkspacePath mark = unsavedPath(target);
            if (unsaved) {
                workspace.get().write(mark, "changes not saved", null);
            } else if (workspace.get().find(mark).isPresent()) {
                workspace.get().delete(mark);
            }
        } catch (final RuntimeException e) {
            // A convenience: changes= then falls back to the mapping saved with the XSLT.
            LOGGER.debug(() -> LogUtil.message("Couldn't keep the mapping sent for {}: {}", target, e.getMessage()), e);
        }
    }

    // The mark beside a target's mapping saying a call left it with changes not saved.
    private static WorkspacePath unsavedPath(final String target) {
        final String path = path(target).path();
        return WorkspacePath.parse(path.substring(0, path.length() - ".json".length()) + ".unsaved.txt");
    }

    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target.
    /// @return The mapping last sent for it: the latest of the user's sessions for one of a build, else this
    /// session's; if any.
    @SuppressWarnings("unchecked")
    static Optional<Map<String, Object>> sent(final ToolCallContext context, final String target) {
        if (NullSafe.isBlankString(target)) {
            return Optional.empty();
        }
        final Optional<Kept> kept = keptWithTheBuild(context, target);
        if (kept.isPresent()) {
            return Optional.of(kept.get().mapping());
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

    /// @param context The call's context.
    /// @param target  The XSLT's uuid, `build/name`, or a draft's target.
    /// @return When the mapping [#sent] gives for it was sent (or drafted), if that was in an earlier session of the
    /// user's: the call building on it says so.
    static Optional<Instant> earlierSession(final ToolCallContext context, final String target) {
        if (NullSafe.isBlankString(target)) {
            return Optional.empty();
        }
        return keptWithTheBuild(context, target)
                .filter(kept -> !Objects.equals(kept.session(), context.session().id()))
                .map(kept -> Instant.ofEpochMilli(kept.sentMs()));
    }

    // Only the draft made with no build stays with the session alone.
    private static boolean isOfABuild(final String target) {
        return !DRAFT.equals(target);
    }

    private static void keepWithTheBuild(final ToolCallContext context,
                                         final String target,
                                         final Map<String, Object> mapping,
                                         final boolean unsaved) {
        if (!isOfABuild(target)) {
            return;
        }
        Optional<SessionWorkspace> userWorkspace = Optional.empty();
        try {
            userWorkspace = context.userWorkspace();
            if (userWorkspace.isPresent()) {
                final Map<String, Object> kept = new LinkedHashMap<>();
                kept.put(TARGET, target);
                kept.put(SESSION, context.session().id());
                kept.put(SENT_MS, System.currentTimeMillis());
                kept.put(UNSAVED, unsaved);
                kept.put(MAPPING, mapping);
                userWorkspace.get().write(path(target), JsonUtil.writeValueAsString(kept, false), null);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Couldn't keep the mapping sent for {} with its build: {}", target,
                    e.getMessage()), e);
            // One left from before would be taken for the newest, over this session's own.
            userWorkspace.ifPresent(workspace -> forget(workspace, target));
        }
    }

    private static void forget(final SessionWorkspace workspace, final String target) {
        try {
            if (workspace.find(path(target)).isPresent()) {
                workspace.delete(path(target));
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Couldn't forget the mapping kept for {}: {}", target,
                    e.getMessage()), e);
        }
    }

    private static Optional<Kept> keptWithTheBuild(final ToolCallContext context, final String target) {
        if (!isOfABuild(target)) {
            return Optional.empty();
        }
        try {
            final Optional<SessionWorkspace> userWorkspace = context.userWorkspace();
            if (userWorkspace.isEmpty() || userWorkspace.get().find(path(target)).isEmpty()) {
                return Optional.empty();
            }
            final String text = String.join("\n", userWorkspace.get().readLines(path(target), 0,
                    Integer.MAX_VALUE));
            final Map<String, Object> kept = JsonValues.map(JsonUtil.readValue(text, Map.class));
            // Two targets may share a file (by digest): only this one's.
            if (!target.equals(kept.get(TARGET)) || !(kept.get(MAPPING) instanceof Map<?, ?>)) {
                return Optional.empty();
            }
            return Optional.of(new Kept(
                    JsonValues.map(kept.get(MAPPING)),
                    Objects.toString(kept.get(SESSION), null),
                    kept.get(SENT_MS) instanceof final Number ms
                            ? ms.longValue()
                            : 0L,
                    Boolean.TRUE.equals(kept.get(UNSAVED))));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No mapping kept with its build for {}: {}", target,
                    e.getMessage()), e);
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


    // --------------------------------------------------------------------------------


    /// A mapping kept with its build: the mapping, the session that sent it, when, and whether that call saved it.
    private record Kept(Map<String, Object> mapping, String session, long sentMs, boolean unsaved) {

    }
}
