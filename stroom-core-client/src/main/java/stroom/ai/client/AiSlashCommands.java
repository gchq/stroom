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
import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptArgument;
import stroom.ai.shared.AiSkill;
import stroom.util.shared.NullSafe;

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
    static final String SKILLS = "/skills";
    /// The start of the command that runs a skill by name, e.g. `/skill:user-activity-timeline`.
    static final String SKILL_PREFIX = "/skill:";

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
            new AiSlashCommand(SKILLS, "[words]",
                    "List the skills you can read, optionally those matching some words",
                    """
                    A skill is a saved procedure (a hunt, a timeline) in a skills doc. Each is a command of its \
                    own, `/skill:<name>`, listed when you type `/skill:`: run one with its parameters as \
                    `name=value` pairs; any required parameter you leave out is asked in a form.

                    Example: `/skills timeline`, then `/skill:user-activity-timeline user=jsmith days=1`"""));

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

    /// @param skill A skill the user can read.
    /// @return Its command, with its parameters: required ones as `name=…`, others in brackets.
    static AiSlashCommand skillCommand(final AiSkill skill) {
        final StringBuilder arguments = new StringBuilder();
        final StringBuilder reference = new StringBuilder("A skill in ")
                .append(NullSafe.string(skill.getFolder()))
                .append(". It is sent to the agent, which follows its steps; any required parameter you leave out "
                        + "is asked in a form.");
        if (!NullSafe.list(skill.getParameters()).isEmpty()) {
            reference.append("\n");
        }
        for (final AiMcpPromptArgument parameter : NullSafe.list(skill.getParameters())) {
            if (arguments.length() > 0) {
                arguments.append(" ");
            }
            arguments.append(parameter.isRequired()
                    ? parameter.getName() + "=…"
                    : "[" + parameter.getName() + "=…]");
            reference.append("\n- *").append(parameter.getName()).append("*")
                    .append(parameter.isRequired()
                            ? " (required)"
                            : "")
                    .append(NullSafe.isBlankString(parameter.getDescription())
                            ? ""
                            : ": " + parameter.getDescription());
        }
        return new AiSlashCommand(skill.getCommand(), arguments.toString(),
                NullSafe.string(skill.getDescription()) + " (skill in " + NullSafe.string(skill.getFolder()) + ")",
                reference.toString());
    }

    /// @param skills The skills the user can read.
    /// @param words  Words to match in their names and descriptions, or empty for all.
    /// @return The list of the skills that match, as Markdown, for the command reference.
    static String skillsReference(final List<AiSkill> skills, final String words) {
        final String wanted = NullSafe.string(words).strip().toLowerCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder("**Skills**");
        if (!wanted.isEmpty()) {
            sb.append(" matching *").append(words.strip()).append("*");
        }
        sb.append("\n\n");
        int count = 0;
        for (final AiSkill skill : NullSafe.list(skills)) {
            final String text = (NullSafe.string(skill.getName()) + " " + NullSafe.string(skill.getDescription()))
                    .toLowerCase(Locale.ROOT);
            if (!wanted.isEmpty() && !matchesAny(text, wanted)) {
                continue;
            }
            count++;
            sb.append("- `").append(skill.getCommand()).append("`: ").append(NullSafe.string(skill.getName()))
                    .append(" (").append(NullSafe.string(skill.getFolder())).append(")");
            if (NullSafe.isNonBlankString(skill.getDescription())) {
                sb.append(" - ").append(skill.getDescription());
            }
            sb.append('\n');
        }
        if (count == 0) {
            sb.append(wanted.isEmpty()
                    ? "There are no skills you can read. Ask the agent to save one from a chat (\"save this as a "
                      + "skill\")."
                    : "No skill matches.").append('\n');
        }
        return sb.toString();
    }

    private static boolean matchesAny(final String text, final String words) {
        for (final String word : words.split("\\s+")) {
            if (!word.isEmpty() && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /// @param prompt An MCP server's prompt.
    /// @return An example of the prompt's command: with each of its required arguments, or if none is, its first.
    static String promptExample(final AiMcpPrompt prompt) {
        final StringBuilder example = new StringBuilder(prompt.getCommand());
        final List<AiMcpPromptArgument> arguments = NullSafe.list(prompt.getArguments());
        for (final AiMcpPromptArgument argument : arguments) {
            if (argument.isRequired() || (argument == arguments.get(0)
                                          && arguments.stream().noneMatch(AiMcpPromptArgument::isRequired))) {
                example.append(' ').append(argument.getName()).append("=<").append(argument.getName()).append('>');
            }
        }
        return example.toString();
    }

}
