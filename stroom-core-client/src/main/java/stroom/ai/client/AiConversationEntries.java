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

import stroom.ai.shared.AiMessageType;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.List;

/// Where the entries of the conversation go. Every message is shown where it arrives, in the order it arrived:
/// the model's reasoning, what it writes to the user while it works, its tool calls and its answer. A tool call's
/// result is shown in the call's entry. The lines showing what is happening now (the reasoning and the answer
/// being written, and the Working line) are kept at the end of the conversation, with the Working line last.
final class AiConversationEntries {

    static final String WORKING_MESSAGE_ID = "ai-working-message";
    static final String LIVE_ANSWER_ID = "ai-live-answer";
    static final String LIVE_THINKING_ID = "ai-live-thinking";
    // The lines showing what is happening now, in the order they are kept at the end of the conversation.
    static final List<String> PROGRESS_IDS = List.of(LIVE_THINKING_ID, LIVE_ANSWER_ID, WORKING_MESSAGE_ID);

    private static final String ENTRY_TITLE_CLASS = "ai-entry-title";
    private static final String CONTENT_CLASS = "ai-details-content";
    private static final String PREVIEW_CLASS = "ai-thinking-preview";
    private static final String CALL_ID_ATTRIBUTE = "data-call-id";
    private static final int PREVIEW_CHARS = 90;

    private AiConversationEntries() {
        // Static methods only.
    }

    /// @param elementId The id of an element of the conversation, or null.
    /// @return True if the element is one of the lines showing what is happening now, kept at the end of the
    /// conversation, rather than a message.
    static boolean isProgress(final String elementId) {
        return elementId != null && PROGRESS_IDS.contains(elementId);
    }

    /// What the model writes to the user while it works is shown in the conversation, except while the Working
    /// line is showing the same text, so it isn't shown twice.
    ///
    /// @param type        The type of a message.
    /// @param message     The message's text.
    /// @param workingText What the Working line says, or null if there is no Working line.
    /// @return True if the message is left to the Working line to show for now.
    static boolean isLeftToWorkingLine(final AiMessageType type, final String message, final String workingText) {
        return type == AiMessageType.INTERIM_RESPONSE
               && workingText != null
               && AiChatTexts.isShownAsProgress(workingText, message);
    }

    /// @param idStr The message id an element of the conversation is marked with, e.g. `2904`, or `2904-actions`
    ///              for the steps a message left to the user.
    /// @return The id of the message the element shows, or -1 if it isn't one stored, e.g. a question still
    /// being sent.
    static int messageIdOf(final String idStr) {
        if (idStr == null) {
            return -1;
        }
        int end = 0;
        while (end < idStr.length() && Character.isDigit(idStr.charAt(end))) {
            end++;
        }
        return end > 0
                ? Integer.parseInt(idStr.substring(0, end))
                : -1;
    }

    /// Moves the lines showing what is happening now to the end of the conversation, in order, with the Working
    /// line last.
    ///
    /// @param container The conversation.
    static void keepProgressAtEnd(final Element container) {
        for (final String id : PROGRESS_IDS) {
            final Element element = findChild(container, id);
            if (element != null && element != container.getLastChild()) {
                container.appendChild(element);
            }
        }
    }

    /// Marks a tool call's entry with the call's id, so its result can be shown in it.
    ///
    /// @param entry The tool call's element.
    /// @param callId The call's id, or null.
    static void markCall(final Element entry, final String callId) {
        if (callId != null) {
            entry.setAttribute(CALL_ID_ATTRIBUTE, callId);
        }
    }

    /// Shows a tool call's result in the call's entry: its outcome added to the entry's title, and its content
    /// below the call's.
    ///
    /// @param container  The conversation.
    /// @param result     The result's element.
    /// @param resultJson The result, as JSON.
    /// @return True if the result was shown in its call's entry, or false if the call isn't shown, in which case
    /// the result is to be shown as an entry of its own.
    static boolean addResultToCall(final Element container, final Element result, final String resultJson) {
        final Element call = findCall(container, AiToolMessages.getString(resultJson, "callId"));
        if (call == null) {
            return false;
        }
        final Element title = findByClass(call, ENTRY_TITLE_CLASS);
        if (title != null) {
            title.setInnerText(title.getInnerText() + AiToolMessages.resultSuffix(resultJson));
        }
        if (AiToolMessages.getBoolean(resultJson, "error")) {
            call.addClassName("ai-message--tool-error");
        }
        final Element callContent = findByClass(call, CONTENT_CLASS);
        final Element resultContent = findByClass(result, CONTENT_CLASS);
        if (callContent != null && resultContent != null) {
            resultContent.addClassName("ai-tool-result");
            callContent.appendChild(resultContent);
        }
        return true;
    }

    /// Shows the model's reasoning as it streams, as a "Thinking…" entry with a preview of its latest line, at the
    /// end of the conversation above the Working line. The entry is updated in place, so it stays open if the
    /// user has opened it.
    ///
    /// @param container The conversation.
    /// @param text      The reasoning so far.
    /// @param html      The reasoning so far, as sanitised HTML.
    static void showLiveThinking(final Element container, final String text, final SafeHtml html) {
        Element live = findChild(container, LIVE_THINKING_ID);
        if (live == null) {
            live = Document.get().createElement("details");
            live.setId(LIVE_THINKING_ID);
            live.setClassName("ai-message ai-message--thinking ai-message--live-thinking");
            final Element summary = Document.get().createElement("summary");
            summary.setClassName("ai-message-header");
            final Element title = Document.get().createSpanElement();
            title.setClassName(ENTRY_TITLE_CLASS);
            title.setInnerText("Thinking…");
            summary.appendChild(title);
            final Element preview = Document.get().createSpanElement();
            preview.setClassName(PREVIEW_CLASS);
            summary.appendChild(preview);
            live.appendChild(summary);
            final Element content = Document.get().createDivElement();
            content.setClassName(CONTENT_CLASS);
            live.appendChild(content);
            container.appendChild(live);
        }
        findByClass(live, PREVIEW_CLASS).setInnerText(lastLine(text));
        findByClass(live, CONTENT_CLASS).setInnerHTML(html.asString());
        keepProgressAtEnd(container);
    }

    /// Removes the live view of the model's reasoning, e.g. once the reasoning has arrived or the run has stopped.
    ///
    /// @param container The conversation.
    static void removeLiveThinking(final Element container) {
        final Element live = findChild(container, LIVE_THINKING_ID);
        if (live != null) {
            live.removeFromParent();
        }
    }

    private static Element findChild(final Element container, final String id) {
        Element child = container.getFirstChildElement();
        while (child != null) {
            if (id.equals(child.getId())) {
                return child;
            }
            child = child.getNextSiblingElement();
        }
        return null;
    }

    /// @return The latest tool call's entry with the call id, looked for from the end of the conversation, as
    /// the result of a call follows it closely.
    private static Element findCall(final Element container, final String callId) {
        if (callId == null) {
            return null;
        }
        Element child = container.getLastChild() != null
                ? container.getLastChild().cast()
                : null;
        while (child != null) {
            if (callId.equals(child.getAttribute(CALL_ID_ATTRIBUTE))) {
                return child;
            }
            child = child.getPreviousSiblingElement();
        }
        return null;
    }

    private static String lastLine(final String text) {
        if (text == null) {
            return "";
        }
        final String[] lines = text.trim().split("\n");
        final String last = lines[lines.length - 1].trim();
        return last.length() > PREVIEW_CHARS
                ? "…" + last.substring(last.length() - PREVIEW_CHARS)
                : last;
    }

    /// @return True if the element has the class. The class attribute is read, as `className` is not a string
    /// for SVG elements.
    private static boolean hasClass(final Element element, final String className) {
        final String classes = element.getAttribute("class");
        return classes != null && (" " + classes + " ").contains(" " + className + " ");
    }

    /// @return The first element at or below an element with a class, or null.
    private static Element findByClass(final Element element, final String className) {
        if (element == null) {
            return null;
        }
        if (hasClass(element, className)) {
            return element;
        }
        Element child = element.getFirstChildElement();
        while (child != null) {
            final Element found = findByClass(child, className);
            if (found != null) {
                return found;
            }
            child = child.getNextSiblingElement();
        }
        return null;
    }
}
