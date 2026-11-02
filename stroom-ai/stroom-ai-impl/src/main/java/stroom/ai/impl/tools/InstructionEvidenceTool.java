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

import stroom.ai.api.AiService;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiInstructionSuggestionsConfig;
import stroom.ai.impl.ElicitationRecord;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.tools.InstructionEvidence.Answer;
import stroom.ai.impl.tools.InstructionEvidence.Chat;
import stroom.ai.impl.tools.InstructionEvidence.ChatRef;
import stroom.ai.impl.tools.InstructionEvidence.Evidence;
import stroom.ai.impl.tools.InstructionEvidence.Mixed;
import stroom.ai.impl.tools.InstructionEvidence.Repeated;
import stroom.ai.impl.tools.InstructionEvidence.Said;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// `instruction_evidence` - what the user's own Stroom AI chats say about how they want things done, as evidence for
/// standing instructions (AGENTS docs): the answers they gave the agent's forms, grouped by question, and what they
/// wrote. Only the calling user's chats, within the configured window ([AiInstructionSuggestionsConfig]). Asked
/// for by the user: choices made again and again in chats were lost when each chat ended.
///
/// Forms can quote sample data and document names, so its result counts as untrusted content; nothing it gives is
/// a rule until the user applies one.
class InstructionEvidenceTool extends AbstractBuiltInTool {

    static final String NAME = "instruction_evidence";
    /// The most chats read, newest first.
    static final int MAX_CHATS = 200;

    private final AiService aiService;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    InstructionEvidenceTool(final AiService aiService, final Provider<AiConfig> aiConfigProvider) {
        this.aiService = aiService;
        this.aiConfigProvider = aiConfigProvider;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public boolean isChatOnly() {
        // Its evidence is the user's chats; the user chose to leave /mcp sessions out.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .note("Reads only the calling user's own chats.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Gives what the user's own recent chats say about how they want things done, as "
                             + "evidence for standing instructions (" + AgentInstructions.DOC_NAME + " docs): "
                             + "the answers they gave your forms, grouped by question (a choice made the same way "
                             + "in several chats and never another way is 'repeated'; one made different ways is "
                             + "'mixed', which is no rule), and what they wrote themselves. Call it when the user "
                             + "asks you to suggest standing instructions, or when a build is promoted. Draft "
                             + "rules only from repeated choices or from what the user said they always want, "
                             + "and cite the chats.")
                .parameters(ObjectSchema.builder().build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final AiInstructionSuggestionsConfig config = NullSafe.getOrElseGet(aiConfigProvider.get(),
                AiConfig::getInstructionSuggestions, AiInstructionSuggestionsConfig::new);
        final long since = System.currentTimeMillis() - config.getEvidenceWindow().toMillis();
        // The calling user's own chats (not MCP sessions' hidden ones), newest first.
        final List<AiChat> chats = aiService.listChats(new FindAiChatHistoryCriteria(new PageRequest(0, MAX_CHATS),
                        null, null)).getValues().stream()
                .filter(chat -> chat.getUpdateTimeMs() >= since)
                .toList();
        final List<Chat> traces = new ArrayList<>();
        for (final AiChat chat : chats) {
            traces.add(trace(chat, aiService.getMessages(chat.getId())));
        }
        final Evidence evidence = InstructionEvidence.collect(traces, config.getMinChats());
        return ToolResult.text(text(evidence, config));
    }

    /// @param chat     A chat.
    /// @param messages Its messages.
    /// @return What it holds as evidence: its accepted forms' answers, and the user's messages.
    static Chat trace(final AiChat chat, final List<AiChatMessage> messages) {
        final List<Answer> answers = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        for (final AiChatMessage message : messages) {
            if (message.getMessageType() == AiMessageType.USER_MESSAGE) {
                said.add(NullSafe.string(message.getMessage()));
            } else if (message.getMessageType() == AiMessageType.ELICITATION) {
                McpElicitations.read(message)
                        .filter(form -> form.status() == ElicitationRecord.Status.ACCEPTED)
                        .ifPresent(form -> answers.addAll(answers(form)));
            }
        }
        return new Chat(chat.getId(), chat.getTitle(), chat.getUpdateTimeMs(), answers, said);
    }

    // One answer for each field given a value, titled from the form's schema.
    private static List<Answer> answers(final ElicitationRecord form) {
        final Map<?, ?> properties = NullSafe.map(form.schema()).get("properties") instanceof final Map<?, ?> map
                ? map
                : Map.of();
        final List<Answer> out = new ArrayList<>();
        NullSafe.map(form.content()).forEach((field, value) -> {
            if (value == null || (value instanceof final String text && text.isBlank())) {
                return;
            }
            final String title = properties.get(field) instanceof final Map<?, ?> property
                                 && property.get("title") instanceof final String text
                    ? text
                    : null;
            out.add(new Answer(form.tool(), field, title, form.message(), value instanceof final Collection<?> list
                    ? JsonUtil.writeValueAsString(list, false)
                    : String.valueOf(value)));
        });
        return out;
    }

    private static String text(final Evidence evidence, final AiInstructionSuggestionsConfig config) {
        final StringBuilder sb = new StringBuilder("Read ").append(evidence.chats())
                .append(" of the user's own chats from the last ").append(config.getEvidenceWindow())
                .append(".\n");
        sb.append("\n## Repeated choices (the same answer in at least ").append(config.getMinChats())
                .append(" chats, never another)\n");
        if (evidence.repeated().isEmpty()) {
            sb.append("\nNone.\n");
        }
        for (final Repeated repeated : evidence.repeated()) {
            sb.append("\n- ").append(question(repeated.question())).append(": '").append(repeated.answer())
                    .append("', in ").append(repeated.chats().size()).append(" chats (").append(chats(repeated
                            .chats())).append(")\n  Asked: ").append(repeated.asked());
        }
        if (!evidence.mixed().isEmpty()) {
            sb.append("\n\n## Mixed choices (answered different ways: no rule)\n");
            for (final Mixed mixed : evidence.mixed()) {
                final List<String> counts = new ArrayList<>();
                mixed.answers().forEach((answer, count) -> counts.add("'" + answer + "' in " + count));
                sb.append("\n- ").append(question(mixed.question())).append(": ")
                        .append(String.join(", ", counts));
            }
        }
        if (!evidence.said().isEmpty()) {
            sb.append("\n\n## What the user wrote (newest first, cut short)\n");
            for (final Said said : evidence.said()) {
                sb.append("\n- [chat ").append(said.chat().id()).append("] ").append(said.text()
                        .replace('\n', ' '));
            }
        }
        sb.append("\n\nDraft standing instructions only from repeated choices, or from what the user said they "
                  + "always want, citing the chats. A preference of the user's own belongs in their memory; a "
                  + "convention for a folder's content belongs in its ").append(AgentInstructions.DOC_NAME)
                .append(" doc: read get_instructions for the folder first and don't repeat what it says. Nothing "
                        + "here is a rule until the user applies it.");
        return sb.toString();
    }

    private static String question(final InstructionEvidence.Question question) {
        return NullSafe.string(question.tool()) + " / " + NullSafe.nonBlankStringElse(question.title(),
                question.field());
    }

    private static String chats(final List<ChatRef> chats) {
        final Map<Integer, String> named = new LinkedHashMap<>();
        chats.forEach(chat -> named.put(chat.id(), chat.title()));
        final List<String> out = new ArrayList<>();
        named.forEach((id, title) -> out.add(id + " '" + NullSafe.string(title) + "'"));
        return String.join(", ", out);
    }
}
