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

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A skill run by name with the chat's slash command, `/skill:<name> name=value ...` (the name as
/// [SkillDoc#slug] gives it, e.g. `/skill:user-activity-timeline user=jsmith`), as the user sent it. The model is
/// given it as a request to read the skill with `find_skills`, with the values typed, which asks the user for any
/// required parameter not given in a form, and then follow its steps.
public final class SkillInvocations {

    /// The command's prefix.
    public static final String PREFIX = "/skill:";
    /// What the model is given in place of the command starts with this.
    public static final String EXPANDED_PREFIX = "[Skill run by name] ";

    private static final Pattern COMMAND = Pattern.compile("^/skill:([A-Za-z0-9_-]+)(?:\\s+(.*))?$",
            Pattern.DOTALL);
    private static final int MAX_TEXT_CHARS = 2_000;

    private SkillInvocations() {
        // Static only.
    }

    /// @param message A question, as the user sent it.
    /// @return The skill run it asks for, if it is the command.
    public static Optional<Invocation> parse(final String message) {
        final Matcher matcher = COMMAND.matcher(NullSafe.string(message).strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final String rest = NullSafe.string(matcher.group(2)).strip();
        final List<String> tokens = tokens(rest);
        final Map<String, String> arguments = new LinkedHashMap<>();
        final boolean named = !tokens.isEmpty() && tokens.stream().allMatch(token -> token.indexOf('=') > 0);
        if (named) {
            tokens.forEach(token -> arguments.put(token.substring(0, token.indexOf('=')).strip(),
                    token.substring(token.indexOf('=') + 1).strip()));
        }
        return Optional.of(new Invocation(matcher.group(1).toLowerCase(Locale.ROOT), Map.copyOf(arguments), named
                ? null
                : NullSafe.isBlankString(rest)
                        ? null
                        : rest.substring(0, Math.min(rest.length(), MAX_TEXT_CHARS))));
    }

    /// @param message A question, as the user sent it.
    /// @return What the model is given for it: the request to run the skill, if it is the command; otherwise the
    /// question as it is.
    public static String expand(final String message) {
        return parse(message).map(Invocation::request).orElse(message);
    }

    private static List<String> tokens(final String text) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (final char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens;
    }


    // --------------------------------------------------------------------------------


    /// A skill run by name.
    ///
    /// @param name      The skill's name as the command gives it.
    /// @param arguments The values typed, by parameter name.
    /// @param text      What was typed after the name if it wasn't `name=value` pairs, or null.
    public record Invocation(String name, Map<String, String> arguments, String text) {

        /// @return What the model is given: to read the skill with find_skills with the values typed, which asks
        /// for any required parameter missing, then follow its steps.
        public String request() {
            final Map<String, Object> call = new LinkedHashMap<>();
            call.put(FindSkillsTool.NAME_ARG, name);
            call.put(FindSkillsTool.ARGUMENTS_ARG, arguments);
            return EXPANDED_PREFIX + "The user asked to run the skill '" + name + "'"
                   + (arguments.isEmpty()
                           ? ""
                           : " with " + JsonUtil.writeValueAsString(arguments, false))
                   + (text == null
                           ? ""
                           : ", saying: " + text)
                   + ". Read it with " + FindSkillsTool.NAME + " " + JsonUtil.writeValueAsString(call, false)
                   + (text == null
                           ? ""
                           : " (the values their words give put in arguments)")
                   + ": it asks the user for any required parameter not given. Then follow its steps.";
        }
    }
}
