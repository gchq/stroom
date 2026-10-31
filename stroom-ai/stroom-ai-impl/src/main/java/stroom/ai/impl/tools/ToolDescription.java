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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// What a tool, or one of its parameters, is and how to use it, as a sequence of parts in the order they are read.
/// Each part is either general (true wherever the tool is called) or chat-only: a hint that only makes sense in the
/// "Ask Stroom AI" chat, e.g. naming another built-in tool (read_file, the table tool), the chat's workspace and its
/// `/outputs/` files, an `out` argument that writes to the workspace, the tab the user attached, or a user-action
/// button.
///
/// It has two renderings:
///
/// * [#forChat()]: every part joined exactly as written, which is what the chat's model is shown.
/// * [#general()]: only the general parts, for a client outside the chat (e.g. over MCP), which has none of the
///   chat's other tools, workspace or tabs. Nothing uses it yet.
///
/// Write each chat-only part so that leaving it out leaves whole sentences, e.g. with its leading space or
/// punctuation: `general("Deletes a file")`, `chatOnly(" in /outputs/")`, `general(" that is no longer needed.")`.
///
/// @param parts The parts, in order.
public record ToolDescription(List<Part> parts) {

    // Punctuation that follows a word directly, so a space before it is dropped when the part before it is left out.
    private static final String CLOSING_PUNCTUATION = ",.;:)";

    /// @param parts The parts, in order.
    public ToolDescription {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
    }

    /// @param text A description that is general throughout, or null.
    /// @return It as a description with one general part, or null if the text is null.
    public static ToolDescription of(final String text) {
        return text == null
                ? null
                : new ToolDescription(List.of(new Part(text, false)));
    }

    /// @return A builder for a description made of general and chat-only parts.
    public static Builder builder() {
        return new Builder();
    }

    /// @param description A description, or null.
    /// @return Its chat rendering ([#forChat()]), or null if it is null.
    public static String forChat(final ToolDescription description) {
        return description == null
                ? null
                : description.forChat();
    }

    /// @return Every part joined exactly as written: the description the chat's model is shown.
    public String forChat() {
        final StringBuilder sb = new StringBuilder();
        parts.forEach(part -> sb.append(part.text()));
        return sb.toString();
    }

    /// @return Only the general parts, joined so that a left-out part leaves no doubled space or space before
    /// punctuation, and stripped: the description for a client outside the chat. Empty if every part is chat-only.
    public String general() {
        final StringBuilder sb = new StringBuilder();
        for (final Part part : parts) {
            if (!part.chatOnly()) {
                append(sb, part.text());
            }
        }
        return sb.toString().strip();
    }

    /// @return True if every part is chat-only, so the description (and the parameter it describes) only has
    /// meaning in the chat.
    public boolean isChatOnly() {
        return !parts.isEmpty() && parts.stream().allMatch(Part::chatOnly);
    }

    /// @return True if any part is chat-only.
    public boolean hasChatOnlyParts() {
        return parts.stream().anyMatch(Part::chatOnly);
    }

    /// @return The description with only its general parts, as one general part ([#general()]), or null if every
    /// part is chat-only.
    public ToolDescription withoutChatOnlyParts() {
        final String general = general();
        return general.isEmpty()
                ? null
                : of(general);
    }

    private static void append(final StringBuilder sb, final String text) {
        String next = text;
        final boolean endsWithSpace = !sb.isEmpty() && Character.isWhitespace(sb.charAt(sb.length() - 1));
        if (endsWithSpace && !next.isEmpty()) {
            if (Character.isWhitespace(next.charAt(0))) {
                next = next.stripLeading();
            } else if (CLOSING_PUNCTUATION.indexOf(next.charAt(0)) >= 0) {
                sb.setLength(sb.length() - 1);
            }
        }
        sb.append(next);
    }

    /// One part of a description.
    ///
    /// @param text     The part's text, exactly as it is joined to the parts either side of it.
    /// @param chatOnly True if it only makes sense in the chat (see [ToolDescription]).
    public record Part(String text, boolean chatOnly) {

        /// @param text     The part's text.
        /// @param chatOnly True if it only makes sense in the chat.
        public Part {
            Objects.requireNonNull(text, "text");
        }
    }

    /// Builds a [ToolDescription] from its parts, in order.
    public static final class Builder {

        private final List<Part> parts = new ArrayList<>();

        private Builder() {
        }

        /// @param text A part that is true wherever the tool is called.
        /// @return This builder.
        public Builder general(final String text) {
            parts.add(new Part(text, false));
            return this;
        }

        /// @param text A part that only makes sense in the chat, e.g. naming another built-in tool or the chat's
        ///             workspace.
        /// @return This builder.
        public Builder chatOnly(final String text) {
            parts.add(new Part(text, true));
            return this;
        }

        /// @return The description.
        public ToolDescription build() {
            return new ToolDescription(parts);
        }
    }
}
