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

import stroom.ai.shared.AiAgentMode;

import java.util.List;
import java.util.Locale;

/// The chat's own slash commands, and the parts of running them that don't need a browser.
final class AiSlashCommands {

    static final String COMPACT = "/compact";
    static final String MODE = "/mode";
    static final String PLAN = "/plan";
    static final String NEW = "/new";
    static final String REMEMBER = "/remember";
    static final String HELP = "/help";

    /// The user memory note that /remember adds to.
    static final String REMEMBER_NOTE = "remembered.md";

    static final List<AiSlashCommand> COMMANDS = List.of(
            new AiSlashCommand(COMPACT, "[focus]",
                    "Compact the chat now, optionally saying what the summary should keep"),
            new AiSlashCommand(MODE, "agent|ask|plan",
                    "Switch this chat to Agent, Ask or Plan mode"),
            new AiSlashCommand(PLAN, "<task>",
                    "Switch to Plan mode and ask for a plan for the task"),
            new AiSlashCommand(NEW, "[question]",
                    "Start a new chat, optionally asking its first question"),
            new AiSlashCommand(REMEMBER, "<fact>",
                    "Add a fact to your memory, which is loaded into all your chats"),
            new AiSlashCommand(HELP, "",
                    "List the commands"));

    private AiSlashCommands() {
        // Static methods only.
    }

    /// @param name A command, with its `/`.
    /// @return True if it is one of the chat's own commands (rather than an MCP server's prompt).
    static boolean isOwn(final String name) {
        return COMMANDS.stream().anyMatch(command -> command.getName().equals(name));
    }

    /// @param text What the user typed after /mode, e.g. "plan".
    /// @return The mode, or null if it isn't one.
    static AiAgentMode parseMode(final String text) {
        if (text == null) {
            return null;
        }
        final String wanted = text.strip().toLowerCase(Locale.ROOT);
        for (final AiAgentMode mode : AiAgentMode.values()) {
            if (mode.getDisplayValue().toLowerCase(Locale.ROOT).equals(wanted)) {
                return mode;
            }
        }
        return null;
    }

    /// @param existing The note's content so far, or null if there is no note yet.
    /// @param fact     The fact to add.
    /// @return The note with the fact added as a bullet.
    static String addFact(final String existing, final String fact) {
        final String bullet = "- " + fact.strip().replace("\n", " ");
        if (existing == null || existing.isBlank()) {
            return "# Remembered\n\n" + bullet + "\n";
        }
        return existing.stripTrailing() + "\n" + bullet + "\n";
    }

    /// @param commands The commands there are: the chat's own, then MCP servers' prompts.
    /// @return A list of the commands, one a line, with what each does.
    static String help(final List<AiSlashCommand> commands) {
        final StringBuilder sb = new StringBuilder("Commands:");
        for (final AiSlashCommand command : commands) {
            sb.append("\n").append(command.getName());
            if (!command.getArguments().isEmpty()) {
                sb.append(" ").append(command.getArguments());
            }
            sb.append(" - ").append(command.getDescription());
        }
        sb.append("\n\nTo send a message that starts with /, start it with //.");
        return sb.toString();
    }
}
