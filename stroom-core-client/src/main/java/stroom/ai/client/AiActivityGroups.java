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

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Groups each turn's reasoning, tool calls, what the model says while it works and other activity into one
/// collapsible element above its answer, so the conversation shows questions and answers, with the work behind
/// each answer a click away. What the model says while it works is in the group, in the order it was said, so it
/// doesn't push the group, and the tool calls added to it, up out of view.
///
/// The group is open while the turn is worked on, with a heading saying what is happening now, and closes when
/// the answer arrives, with a heading saying how long it took and what was done. If something else is shown in
/// the conversation while the turn is worked on, e.g. a form the user answers, the group closes there and the
/// turn's later activity goes in a new group after it, so the conversation reads in the order things happened.
/// Each entry in a group is its own `<details>` element, so what the user has opened stays open as more arrives.
final class AiActivityGroups {

    static final String MESSAGE_ID_ATTRIBUTE = "data-message-id";
    private static final String GROUP_CLASS = "ai-activity";
    private static final String TITLE_CLASS = "ai-activity-title";
    private static final String ENTRIES_CLASS = "ai-activity-entries";
    private static final String ENTRY_TITLE_CLASS = "ai-entry-title";
    private static final String CONTENT_CLASS = "ai-details-content";
    private static final String LIVE_CLASS = "ai-activity-live";
    private static final String PREVIEW_CLASS = "ai-thinking-preview";
    private static final String CALL_ID_ATTRIBUTE = "data-call-id";
    private static final int PREVIEW_CHARS = 90;
    // The lines showing what is happening now, kept at the end of the conversation, rather than part of it.
    private static final String[] PROGRESS_CLASSES = {"ai-message--working", "ai-message--streaming"};

    // The latest group of each turn.
    private final Map<Integer, Group> groups = new HashMap<>();
    // Groups closed when something else was shown after them while their turn was worked on.
    private final List<Group> earlierGroups = new ArrayList<>();

    /// @param type A message type.
    /// @return True if messages of the type belong in a turn's activity group, rather than in the conversation.
    static boolean isActivity(final AiMessageType type) {
        return type == AiMessageType.THINKING
               || type == AiMessageType.INTERIM_RESPONSE
               || type == AiMessageType.TOOL_CALL
               || type == AiMessageType.TOOL_RESULT
               || type == AiMessageType.MEMORY_UPDATE
               || type == AiMessageType.DEBUG_DETAIL;
    }

    /// Adds an activity message's element to its turn's group, making the group (at the end of the conversation)
    /// if the turn doesn't have one yet. A tool call's result is added to the call's entry, rather than as an
    /// entry of its own.
    ///
    /// @param container      The conversation.
    /// @param msg            The message.
    /// @param entry          The message's element: a `<details>` element whose summary text is in an element
    ///                       with the class `ai-entry-title`, or for what the model said, the message as shown in
    ///                       the conversation.
    /// @param questionTimeMs When the turn's question was asked, or 0 if not known.
    void add(final Element container, final AiChatMessage msg, final Element entry, final long questionTimeMs) {
        final Group group = getOrCreate(container, msg.getTurnId(), questionTimeMs, msg.getCreateTimeMs());
        final String json = msg.getMessage();
        if (msg.getMessageType() == AiMessageType.TOOL_RESULT) {
            final Element call = findCall(group, AiToolMessages.getString(json, "callId"));
            if (call != null) {
                addResultToCall(call, entry, json);
                group.lastTimeMs = msg.getCreateTimeMs();
                updateTitle(group);
                return;
            }
        } else if (msg.getMessageType() == AiMessageType.TOOL_CALL) {
            final String callId = AiToolMessages.getString(json, "callId");
            if (callId != null) {
                entry.setAttribute(CALL_ID_ATTRIBUTE, callId);
            }
            group.tools++;
        } else if (msg.getMessageType() == AiMessageType.THINKING) {
            group.thoughts++;
        }
        // Reasoning that has arrived replaces the live view of it.
        removeLive(group);
        group.entries.appendChild(entry);
        group.lastTimeMs = msg.getCreateTimeMs();
        // The heading names the step being taken, which what the model said isn't.
        if (msg.getMessageType() != AiMessageType.DEBUG_DETAIL
            && msg.getMessageType() != AiMessageType.INTERIM_RESPONSE) {
            group.currentStep = entryTitle(entry);
        }
        updateTitle(group);
    }

    /// Shows the model's reasoning as it streams, in the turn's group, as a "Thinking…" entry with a preview of
    /// its latest line. The entry is updated in place, so it stays open if the user has opened it.
    ///
    /// @param container The conversation.
    /// @param streaming      The STREAMING_THINKING message.
    /// @param html           The reasoning so far, as sanitised HTML.
    /// @param questionTimeMs When the turn's question was asked, or 0 if not known.
    void showLiveThinking(final Element container,
                          final AiChatMessage streaming,
                          final SafeHtml html,
                          final long questionTimeMs) {
        final Group group = getOrCreate(container, streaming.getTurnId(), questionTimeMs,
                streaming.getCreateTimeMs());
        Element live = findByClass(group.entries, LIVE_CLASS);
        if (live == null) {
            live = Document.get().createElement("details");
            live.setClassName("ai-message ai-message--thinking " + LIVE_CLASS);
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
            group.entries.appendChild(live);
        }
        findByClass(live, PREVIEW_CLASS).setInnerText(lastLine(streaming.getMessage()));
        findByClass(live, CONTENT_CLASS).setInnerHTML(html.asString());
        group.currentStep = "Thinking";
        updateTitle(group);
    }

    /// Removes any live view of the model's reasoning, e.g. once the run has stopped.
    void removeLiveThinking() {
        for (final Group group : groups.values()) {
            removeLive(group);
        }
    }

    /// Closes a turn's group now its answer has arrived, with a heading saying how long the turn took and what
    /// was done.
    ///
    /// @param turnId    The turn.
    /// @param endTimeMs When the answer arrived.
    void finish(final int turnId, final long endTimeMs) {
        final Group group = groups.get(turnId);
        if (group != null && !group.finished) {
            group.finished = true;
            group.lastTimeMs = Math.max(group.lastTimeMs, endTimeMs);
            removeLive(group);
            group.element.removeAttribute("open");
            updateTitle(group);
        }
    }

    /// Closes every group still open, as nothing more is coming, e.g. when the run was stopped.
    void finishAll() {
        for (final Group group : new ArrayList<>(groups.values())) {
            finish(group.turnId, group.lastTimeMs);
        }
    }

    /// @return The group of the latest turn still being worked on, or null if there isn't one.
    Element getLatestUnfinished() {
        Group latest = null;
        for (final Group group : groups.values()) {
            if (!group.finished && (latest == null || group.turnId > latest.turnId)) {
                latest = group;
            }
        }
        return latest != null
                ? latest.element
                : null;
    }

    /// @param messageId A message id.
    /// @return The message's entry in a group, or null if it is not in one.
    Element findEntry(final String messageId) {
        for (final Group group : allGroups()) {
            Element child = group.entries.getFirstChildElement();
            while (child != null) {
                if (messageId.equals(child.getAttribute(MESSAGE_ID_ATTRIBUTE))) {
                    return child;
                }
                child = child.getNextSiblingElement();
            }
        }
        return null;
    }

    /// Removes the entries of messages from a message onwards, and any group left with none, e.g. when a
    /// question is edited.
    ///
    /// @param fromMessageId The first message to remove.
    /// @param keepFirst     True to keep that message itself.
    void removeFrom(final int fromMessageId, final boolean keepFirst) {
        final List<Group> emptied = new ArrayList<>();
        for (final Group group : allGroups()) {
            Element child = group.entries.getFirstChildElement();
            while (child != null) {
                final Element next = child.getNextSiblingElement();
                final String idStr = child.getAttribute(MESSAGE_ID_ATTRIBUTE);
                if (idStr != null && !idStr.isEmpty()) {
                    final int id = Integer.parseInt(idStr);
                    if (id > fromMessageId || (id == fromMessageId && !keepFirst)) {
                        child.removeFromParent();
                    }
                }
                child = next;
            }
            if (group.entries.getFirstChildElement() == null) {
                group.element.removeFromParent();
                emptied.add(group);
            }
        }
        for (final Group group : emptied) {
            if (!earlierGroups.remove(group)) {
                groups.remove(group.turnId);
            }
        }
    }

    /// Forgets every group, e.g. when the conversation is cleared.
    void clear() {
        groups.clear();
        earlierGroups.clear();
    }

    private List<Group> allGroups() {
        final List<Group> all = new ArrayList<>(earlierGroups);
        all.addAll(groups.values());
        return all;
    }

    /// @param questionTimeMs When the turn's question was asked, or 0 if not known.
    /// @param timeMs         When the activity being added happened.
    /// @return The turn's group to add to, made at the end of the conversation if needed. A turn's first group
    /// times its work from when the question was asked, and any later group from its first activity.
    private Group getOrCreate(final Element container,
                              final int turnId,
                              final long questionTimeMs,
                              final long timeMs) {
        Group group = groups.get(turnId);
        long startTimeMs = questionTimeMs > 0
                ? questionTimeMs
                : timeMs;
        if (group != null && !group.finished && isFollowedByConversation(group.element)) {
            // Something has been shown after the group, e.g. a form the user has answered, so what follows goes
            // after it, in a group of its own, starting now.
            finish(turnId, group.lastTimeMs);
            earlierGroups.add(group);
            groups.remove(turnId);
            group = null;
            startTimeMs = timeMs;
        }
        if (group == null) {
            final Element element = Document.get().createElement("details");
            element.setClassName("ai-message " + GROUP_CLASS);
            element.setAttribute("open", "");
            element.setAttribute("data-activity-turn", String.valueOf(turnId));
            final Element summary = Document.get().createElement("summary");
            summary.setClassName("ai-message-header");
            final Element title = Document.get().createSpanElement();
            title.setClassName(TITLE_CLASS);
            summary.appendChild(title);
            element.appendChild(summary);
            final Element entries = Document.get().createDivElement();
            entries.setClassName(ENTRIES_CLASS);
            element.appendChild(entries);
            container.appendChild(element);
            group = new Group(turnId, element, title, entries, startTimeMs);
            groups.put(turnId, group);
        }
        return group;
    }

    /// @return True if anything other than the lines showing what is happening now follows the element.
    private static boolean isFollowedByConversation(final Element element) {
        Element next = element.getNextSiblingElement();
        while (next != null) {
            if (!isProgress(next)) {
                return true;
            }
            next = next.getNextSiblingElement();
        }
        return false;
    }

    private static boolean isProgress(final Element element) {
        for (final String className : PROGRESS_CLASSES) {
            if (hasClass(element, className)) {
                return true;
            }
        }
        return false;
    }

    private static Element findCall(final Group group, final String callId) {
        if (callId == null) {
            return null;
        }
        Element child = group.entries.getFirstChildElement();
        while (child != null) {
            if (callId.equals(child.getAttribute(CALL_ID_ATTRIBUTE))) {
                return child;
            }
            child = child.getNextSiblingElement();
        }
        return null;
    }

    private static void addResultToCall(final Element call, final Element result, final String resultJson) {
        final Element title = findByClass(call, ENTRY_TITLE_CLASS);
        if (title != null) {
            title.setInnerText(title.getInnerText() + AiToolMessages.resultSuffix(resultJson));
        }
        if (AiToolMessages.getBoolean(resultJson, "error")) {
            call.addClassName("ai-activity-entry--error");
        }
        final Element callContent = findByClass(call, CONTENT_CLASS);
        final Element resultContent = findByClass(result, CONTENT_CLASS);
        if (callContent != null && resultContent != null) {
            resultContent.addClassName("ai-tool-result");
            callContent.appendChild(resultContent);
        }
    }

    private static void removeLive(final Group group) {
        final Element live = findByClass(group.entries, LIVE_CLASS);
        if (live != null) {
            live.removeFromParent();
        }
    }

    private static void updateTitle(final Group group) {
        final String text;
        if (group.finished) {
            final StringBuilder sb = new StringBuilder("Worked for ")
                    .append(AiToolMessages.duration(Math.max(0, group.lastTimeMs - group.startTimeMs)));
            if (group.tools > 0) {
                sb.append(" · ").append(group.tools).append(group.tools == 1
                        ? " tool"
                        : " tools");
            }
            if (group.thoughts > 0) {
                sb.append(" · ").append(group.thoughts).append(group.thoughts == 1
                        ? " thought"
                        : " thoughts");
            }
            text = sb.toString();
        } else {
            text = group.currentStep != null
                    ? "Working… · " + group.currentStep
                    : "Working…";
        }
        group.title.setInnerText(text);
    }

    private static String entryTitle(final Element entry) {
        final Element title = findByClass(entry, ENTRY_TITLE_CLASS);
        return title != null
                ? title.getInnerText()
                : null;
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


    // --------------------------------------------------------------------------------


    /// A turn's group, and what is known about it.
    private static final class Group {

        private final int turnId;
        private final Element element;
        private final Element title;
        private final Element entries;
        private final long startTimeMs;
        private long lastTimeMs;
        private int tools;
        private int thoughts;
        private String currentStep;
        private boolean finished;

        private Group(final int turnId,
                      final Element element,
                      final Element title,
                      final Element entries,
                      final long startTimeMs) {
            this.turnId = turnId;
            this.element = element;
            this.title = title;
            this.entries = entries;
            this.startTimeMs = startTimeMs;
            this.lastTimeMs = startTimeMs;
        }
    }
}
