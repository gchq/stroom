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

import stroom.ai.shared.AiChatMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Writes what a chat stored, and what the model was sent, as plain text for reference files, the same every
/// time: anything that changes from run to run (times, durations, expiry times, the sequence number in a
/// proposed query's button id) is replaced with a fixed placeholder, JSON is laid out a field a line, and very
/// long text is shortened to its start, its end, its length and a hash of the whole, so a change to any of it
/// still shows.
final class ChatTranscripts {

    /// Texts longer than this are shortened.
    static final int MAX_SHOWN_CHARS = 3_000;
    private static final int SHOWN_HEAD_CHARS = 1_200;
    private static final int SHOWN_TAIL_CHARS = 200;
    // Numbers that are times or durations, which change from run to run.
    private static final Set<String> TIME_FIELDS = Set.of("durationMs", "expiresMs", "createTimeMs",
            "updateTimeMs");
    private static final String TIME_PLACEHOLDER = "<time>";
    // propose_query numbers its buttons from a sequence shared by every chat on the node.
    private static final Pattern BUTTON_ID = Pattern.compile("create-query-\\d+");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatTranscripts() {
        // Static only.
    }

    /// @param messages Stored chat messages.
    /// @return Each message's id, type, turn and attachment, then its text (laid out if it is JSON).
    static String storedMessages(final List<AiChatMessage> messages) {
        return storedMessages(messages, Map.of());
    }

    /// @param messages    Stored chat messages, as they are now.
    /// @param firstStored The same messages as they were first stored, by id, for those changed since (e.g. a card
    ///                    the user has answered).
    /// @return Each message's id, type, turn and attachment, then its text (laid out if it is JSON), and for a
    /// message changed since it was stored, what it was first stored as.
    static String storedMessages(final List<AiChatMessage> messages,
                                 final Map<Integer, AiChatMessage> firstStored) {
        final StringBuilder sb = new StringBuilder();
        for (final AiChatMessage message : messages) {
            sb.append("#").append(message.getId()).append(' ').append(message.getMessageType())
                    .append(" (turn ").append(message.getTurnId());
            if (message.getAttachmentId() != null) {
                sb.append(", attachment ").append(message.getAttachmentId());
            }
            sb.append(")\n").append(indent(storedText(message.getMessage()))).append("\n");
            final AiChatMessage first = firstStored.get(message.getId());
            if (first != null && (first.getMessageType() != message.getMessageType()
                                  || !Objects.equals(first.getMessage(), message.getMessage()))) {
                sb.append("  First stored as ").append(first.getMessageType()).append(":\n")
                        .append(indent(storedText(first.getMessage()))).append("\n");
            }
        }
        return sb.toString();
    }

    /// @param text A stored message's text.
    /// @return The text, normalised, laid out a field a line if it is JSON.
    static String storedText(final String text) {
        if (text == null) {
            return "(null)";
        }
        final String trimmed = text.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                final JsonNode json = MAPPER.readTree(trimmed);
                if (json != null && json.isContainerNode()) {
                    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalise(json));
                }
            } catch (final JsonProcessingException e) {
                // Not JSON after all, so shown as text.
            }
        }
        return normaliseText(text);
    }

    /// @param requests  The requests the model was given, in order.
    /// @param builtIns  The names of the built-in tools, which are listed in name order (they are registered in
    ///                  no particular order), ahead of the MCP tools, which are listed in the order offered.
    /// @return Each request: the tools offered, then each message.
    static String requests(final List<ChatRequest> requests, final Set<String> builtIns) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            final ChatRequest request = requests.get(i);
            sb.append("--- Request ").append(i + 1).append(" ---\n")
                    .append("Tools offered: ").append(toolNames(request.toolSpecifications(), builtIns))
                    .append("\n").append(messages(request.messages()));
        }
        return sb.toString();
    }

    /// @param messages Messages for the model.
    /// @return Each message, numbered, with its kind and content.
    static String messages(final List<ChatMessage> messages) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            sb.append("[").append(i).append("] ").append(message(messages.get(i))).append("\n");
        }
        return sb.toString();
    }

    /// @param specifications Tool definitions.
    /// @return Each tool's name, description and parameters (as the JSON schema the model is sent).
    static String toolDefinitions(final Collection<ToolSpecification> specifications) {
        final StringBuilder sb = new StringBuilder();
        for (final ToolSpecification specification : specifications) {
            sb.append(specification.name()).append("\n")
                    .append(indent("Description: " + normaliseText(String.valueOf(specification.description()))))
                    .append("\n");
            final Object parameters = specification.parameters() == null
                    ? null
                    : JsonSchemaElementUtils.toMap(specification.parameters());
            sb.append(indent("Parameters: " + json(parameters))).append("\n");
        }
        return sb.toString();
    }

    /// @param text Text, which may hold something that changes from run to run.
    /// @return The text, normalised, and shortened if it is very long.
    static String normaliseText(final String text) {
        return shorten(BUTTON_ID.matcher(text).replaceAll("create-query-N"));
    }

    private static String toolNames(final List<ToolSpecification> specifications, final Set<String> builtIns) {
        if (specifications == null || specifications.isEmpty()) {
            return "(none)";
        }
        final List<String> names = new ArrayList<>(specifications.stream()
                .map(ToolSpecification::name)
                .filter(builtIns::contains)
                .sorted()
                .toList());
        specifications.stream()
                .map(ToolSpecification::name)
                .filter(name -> !builtIns.contains(name))
                .forEach(names::add);
        return String.join(", ", names);
    }

    private static String message(final ChatMessage message) {
        return switch (message) {
            case final SystemMessage system -> "SYSTEM\n" + indent(normaliseText(system.text()));
            case final UserMessage user -> "USER\n" + indent(normaliseText(user.hasSingleText()
                    ? user.singleText()
                    : String.valueOf(user.contents())));
            case final AiMessage ai -> aiMessage(ai);
            case final ToolExecutionResultMessage result -> "TOOL RESULT for " + result.id() + " ("
                                                            + result.toolName() + ")\n"
                                                            + indent(normaliseText(result.text()));
            default -> message.type() + "\n" + indent(normaliseText(String.valueOf(message)));
        };
    }

    private static String aiMessage(final AiMessage ai) {
        final StringBuilder sb = new StringBuilder("AI");
        if (ai.thinking() != null) {
            sb.append("\n").append(indent("Thinking: " + normaliseText(ai.thinking())));
        }
        if (ai.text() != null) {
            sb.append("\n").append(indent(normaliseText(ai.text())));
        }
        for (final ToolExecutionRequest request : ai.toolExecutionRequests()) {
            sb.append("\n").append(indent("Calls " + request.name() + " as " + request.id() + " with "
                                          + normaliseText(String.valueOf(request.arguments()))));
        }
        return sb.toString();
    }

    private static JsonNode normalise(final JsonNode json) {
        if (json instanceof final ObjectNode object) {
            final Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            final List<String> times = new ArrayList<>();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                if (TIME_FIELDS.contains(field.getKey()) && field.getValue().isNumber()) {
                    times.add(field.getKey());
                } else {
                    field.setValue(normalise(field.getValue()));
                }
            }
            times.forEach(name -> object.put(name, TIME_PLACEHOLDER));
            return object;
        }
        if (json instanceof final ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, normalise(array.get(i)));
            }
            return array;
        }
        if (json.isTextual()) {
            return TextNode.valueOf(normaliseText(json.asText()));
        }
        return json;
    }

    private static String shorten(final String text) {
        if (text.length() <= MAX_SHOWN_CHARS) {
            return text;
        }
        return text.substring(0, SHOWN_HEAD_CHARS)
               + "\n[… shortened for this file: " + text.length() + " characters in all, SHA-256 " + sha256(text)
               + " …]\n"
               + text.substring(text.length() - SHOWN_TAIL_CHARS);
    }

    /// @param text Text.
    /// @return The SHA-256 hash of the text, as hex.
    static String sha256(final String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String json(final Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String indent(final String text) {
        return "    " + text.replace("\n", "\n    ");
    }
}
