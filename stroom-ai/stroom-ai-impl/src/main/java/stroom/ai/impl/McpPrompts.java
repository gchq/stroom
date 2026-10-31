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

package stroom.ai.impl;

import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptArgument;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.Prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Turns the prompts MCP servers offer into slash commands for the chat's input box.
final class McpPrompts {

    /// Commands the chat has itself, which a prompt can't take.
    static final Set<String> RESERVED = Set.of("/compact");

    private McpPrompts() {
        // Static only.
    }

    /// @param prompts The servers' prompts.
    /// @return A command for each: `/name`, or `/server.name` if more than one server offers a prompt with the
    /// name, or the name is one of the chat's own commands.
    static List<AiMcpPrompt> commands(final List<McpToolset.ServerPrompt> prompts) {
        final Map<String, Long> nameCounts = prompts.stream()
                .collect(Collectors.groupingBy(prompt -> clean(prompt.prompt().name()), Collectors.counting()));
        final List<AiMcpPrompt> commands = new ArrayList<>();
        for (final McpToolset.ServerPrompt serverPrompt : prompts) {
            final Prompt prompt = serverPrompt.prompt();
            final String plain = "/" + clean(prompt.name());
            final String command = nameCounts.get(clean(prompt.name())) > 1 || RESERVED.contains(plain)
                    ? "/" + clean(serverPrompt.server()) + "." + clean(prompt.name())
                    : plain;
            final List<AiMcpPromptArgument> arguments = NullSafe.list(prompt.arguments()).stream()
                    .map(argument -> new AiMcpPromptArgument(argument.name(), argument.description(),
                            Boolean.TRUE.equals(argument.required())))
                    .toList();
            commands.add(new AiMcpPrompt(serverPrompt.server(), prompt.name(), command,
                    NullSafe.nonBlankStringElse(prompt.description(), prompt.title()), arguments));
        }
        return commands.stream()
                .collect(Collectors.toMap(AiMcpPrompt::getCommand, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    /// @return The name made safe to type in a command: no spaces, lower case.
    private static String clean(final String name) {
        return NullSafe.string(name).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }
}
