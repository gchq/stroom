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
    static final String CLEAR = "/clear";
    static final String REMEMBER = "/remember";
    static final String HELP = "/help";

    /// The user memory note that /remember adds to.
    static final String REMEMBER_NOTE = "remembered.md";

    static final List<AiSlashCommand> COMMANDS = List.of(
            new AiSlashCommand(COMPACT, "[focus]",
                    "Compact the chat now, optionally saying what the summary should keep",
                    """
                    Earlier turns are replaced, in what the model sees, by short digests and a summary the model \
                    writes, so the chat uses less of its context window. Nothing is deleted: the model can still \
                    look turns up, and the compaction can be undone from its card.

                    - *focus*: what the summary should concentrate on.

                    Example: `/compact keep the firewall findings`"""),
            new AiSlashCommand(MODE, "agent|ask|plan",
                    "Switch this chat to Agent, Ask or Plan mode",
                    """
                    - **agent**: does what you ask, with all its tools.
                    - **ask**: answers questions, and changes nothing.
                    - **plan**: works out a plan for what you ask, without carrying it out.

                    Example: `/mode ask`"""),
            new AiSlashCommand(PLAN, "<task>",
                    "Switch to Plan mode and ask for a plan for the task",
                    """
                    The same as `/mode plan`, then asking for the task. Switch back to Agent mode \
                    (`/mode agent`) to carry the plan out.

                    Example: `/plan onboard feed ACME-EVENTS`"""),
            new AiSlashCommand(NEW, "[question]",
                    "Start a new chat, optionally asking its first question",
                    """
                    If this chat has nothing in it yet, it is used rather than starting another.

                    Example: `/new how many events did ACME send yesterday?`"""),
            new AiSlashCommand(CLEAR, "",
                    "Clear this chat's messages, attached data, files and memory, so it starts afresh",
                    """
                    Asks first, and stops anything the chat is doing. The chat itself, its mode and its tool \
                    approvals are kept, as is your own memory. This can't be undone."""),
            new AiSlashCommand(REMEMBER, "<fact>",
                    "Add a fact to your memory, which is loaded into all your chats",
                    """
                    The fact is added to the note `remembered.md` in your memory, which you can see and edit from \
                    the chat menu's **Memory**.

                    Example: `/remember our firewall feeds all start with FW-`"""),
            new AiSlashCommand(HELP, "",
                    "List the commands",
                    """
                    To send a message that starts with `/` as it is, start it with `//`."""));

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

    /// Said with [#help], which is shown as an alert's detail: an alert's message is cut off when long, and the
    /// list grows with MCP servers' prompts.
    static final String HELP_MESSAGE = "The commands, and what each does, are below. To send a message that "
                                       + "starts with /, start it with //.";

    /// @param commands The commands there are: the chat's own, then MCP servers' prompts.
    /// @return A list of the commands, one a line, with what each does.
    static String help(final List<AiSlashCommand> commands) {
        final StringBuilder sb = new StringBuilder();
        for (final AiSlashCommand command : commands) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(command.getName());
            if (!command.getArguments().isEmpty()) {
                sb.append(" ").append(command.getArguments());
            }
            sb.append(" - ").append(command.getDescription());
        }
        return sb.toString();
    }
}
