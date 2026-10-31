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

import stroom.mcp.impl.tools.FormsRequired;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The user's approval of a tool call, made over MCP, that changes something in Stroom. Every such call asks: a
/// client's model is not Stroom's, and Stroom can't tell what it was asked to do.
///
/// The user is asked in a yes or no form their client shows (MCP elicitation). A client that can't show forms
/// can't make such a call at all (see [FormsRequired]): it is never given an id to pass back instead, as its model
/// could approve its own call with one.
///
/// What is shown leads with the arguments that say what the call acts on (a target's uuid or name, the build, the
/// tool), then the arguments, cut at [#MAX_SHOWN_ARGUMENTS] characters, saying so when they are.
@Singleton
class McpApprovals {

    /// The most characters of a call's arguments a form shows.
    static final int MAX_SHOWN_ARGUMENTS = 1_500;
    /// The most characters of one argument shown among the arguments that say what the call acts on.
    static final int MAX_SHOWN_KEY_VALUE = 200;
    // The arguments that say what a call acts on, shown first: those named so, or ending so (e.g. pipeline_uuid).
    private static final Set<String> KEY_NAMES = Set.of("build", "tool", "target", "feed", "pipeline", "index");
    private static final List<String> KEY_ENDINGS = List.of("uuid", "name");

    private final McpForms forms;

    @Inject
    McpApprovals(final McpForms forms) {
        this.forms = forms;
    }

    /// Asks the user to approve a call that changes something.
    ///
    /// @param session   The session the call is made in.
    /// @param exchange  The request.
    /// @param toolName  The tool.
    /// @param arguments The call's arguments, as JSON.
    /// @return Empty if the user approved it, so it may be made; otherwise what the client is told instead.
    Optional<String> approve(final McpSession session,
                             final McpAsyncServerExchange exchange,
                             final String toolName,
                             final String arguments) {
        if (!session.canShowForms()) {
            return Optional.of(FormsRequired.noForms(toolName));
        }
        final ElicitResult result = forms.ask(session, exchange, formMessage(session, toolName, arguments),
                McpForms.NO_FIELDS);
        return result.action() == ElicitResult.Action.ACCEPT
                ? Optional.empty()
                : Optional.of(result.action() == ElicitResult.Action.DECLINE
                        ? "Not run: the user did not allow this call. Don't try it again the same way: ask them "
                          + "what they want instead."
                        : "Not run: the user was asked to allow this call, as it changes something in Stroom, "
                          + "but did not answer.");
    }

    private static String formMessage(final McpSession session, final String toolName, final String arguments) {
        return session.clientName() + " wants to use Stroom's " + toolName + " tool, which changes something in "
               + "Stroom, as you, with " + shown(arguments) + ". Allow it?";
    }

    /// @param arguments A call's arguments, as JSON.
    /// @return How they are shown to the user: the arguments that say what the call acts on, then all of them,
    /// cut at [#MAX_SHOWN_ARGUMENTS] characters, saying so if they are.
    static String shown(final String arguments) {
        final String text = NullSafe.nonBlankStringElse(arguments, "no arguments").strip();
        final String keys = keyArguments(text);
        final String prefix = keys.isEmpty()
                ? ""
                : keys + "; all arguments: ";
        return isCut(text)
                ? prefix + text.substring(0, MAX_SHOWN_ARGUMENTS) + "… (cut: only the first " + MAX_SHOWN_ARGUMENTS
                  + " of their " + text.length() + " characters are shown)"
                : prefix + text;
    }

    private static boolean isCut(final String arguments) {
        return NullSafe.trim(arguments).length() > MAX_SHOWN_ARGUMENTS;
    }

    // The arguments that say what the call acts on, e.g. "pipeline_uuid=..., build=...", or "" if none.
    private static String keyArguments(final String arguments) {
        final Map<?, ?> parsed;
        try {
            parsed = JsonUtil.readValue(arguments, Map.class);
        } catch (final RuntimeException e) {
            return "";
        }
        final List<String> keys = new ArrayList<>();
        NullSafe.map(parsed).forEach((name, value) -> {
            if (isKeyName(String.valueOf(name)) && isScalar(value)) {
                final String shownValue = String.valueOf(value);
                keys.add(name + "=" + (shownValue.length() > MAX_SHOWN_KEY_VALUE
                        ? shownValue.substring(0, MAX_SHOWN_KEY_VALUE) + "…"
                        : shownValue));
            }
        });
        return String.join(", ", keys);
    }

    private static boolean isKeyName(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return KEY_NAMES.contains(lower) || KEY_ENDINGS.stream().anyMatch(lower::endsWith);
    }

    private static boolean isScalar(final Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
