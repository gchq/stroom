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

package stroom.ai.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A command the user can type in the chat's input box, starting with `/`, e.g. `/compact`.
public final class AiSlashCommand {

    private final String name;
    private final String arguments;
    private final String description;

    /// @param name        The command, with its `/`, e.g. `/compact`.
    /// @param arguments   What may follow it, e.g. `[focus]`, or an empty string.
    /// @param description What it does.
    public AiSlashCommand(final String name, final String arguments, final String description) {
        this.name = name;
        this.arguments = arguments;
        this.description = description;
    }

    /// @return The command, with its `/`.
    public String getName() {
        return name;
    }

    /// @return What may follow the command, or an empty string.
    public String getArguments() {
        return arguments;
    }

    /// @return What the command does.
    public String getDescription() {
        return description;
    }

    /// Reads the arguments the user typed after a command: `name=value` pairs, with values in double quotes if
    /// they have spaces, or else the whole text as the value of the command's first argument.
    ///
    /// @param firstArgument The command's first argument's name, or null if it has none.
    /// @param text          What the user typed after the command.
    /// @return The arguments, by name.
    public static Map<String, String> parseArguments(final String firstArgument, final String text) {
        final Map<String, String> arguments = new HashMap<>();
        final String trimmed = text == null
                ? ""
                : text.trim();
        if (trimmed.isEmpty()) {
            return arguments;
        }
        final List<String> tokens = new ArrayList<>();
        final StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (final char c : trimmed.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ' ' && !quoted) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        final boolean named = tokens.stream().allMatch(part -> part.indexOf('=') > 0);
        if (named) {
            for (final String part : tokens) {
                arguments.put(part.substring(0, part.indexOf('=')), part.substring(part.indexOf('=') + 1));
            }
        } else if (firstArgument != null) {
            arguments.put(firstArgument, String.join(" ", tokens));
        }
        return arguments;
    }
}
