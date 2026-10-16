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

import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatStatus;
import stroom.svg.shared.SvgImage;

/// What the chat panel says and shows about chats and their parts, kept out of the presenter so it can be
/// tested without a browser.
final class AiChatTexts {

    // The context pill turns yellow, then red, as the context window fills.
    static final int CONTEXT_WARN_PERCENT = 75;
    static final int CONTEXT_OVER_PERCENT = 90;

    private AiChatTexts() {
        // Static methods only.
    }

    /// @param status A chat's status.
    /// @return The icon for the chat in the list of chats: whether it needs the user, is working, failed,
    /// stopped or is unread, most important first.
    static SvgImage statusIcon(final AiChatStatus status) {
        if (status.isNeedsYou()) {
            return SvgImage.ALERT;
        } else if (status.isBusy()) {
            return SvgImage.SPINNER_SPINNING;
        } else if (status.getRunStatus() == AiChatRunStatus.FAILED) {
            return SvgImage.ERROR;
        } else if (status.getRunStatus() == AiChatRunStatus.INTERRUPTED
                   || status.getRunStatus() == AiChatRunStatus.STOPPED) {
            return SvgImage.PAUSE;
        } else if (status.isUnread()) {
            return SvgImage.DOT;
        }
        return SvgImage.AI;
    }

    /// @param percent How full the model's context window is.
    /// @return The context pill's colour: none (grey), "warn" (yellow) from [#CONTEXT_WARN_PERCENT], or "over"
    /// (red) from [#CONTEXT_OVER_PERCENT].
    static String contextLevel(final int percent) {
        if (percent >= CONTEXT_OVER_PERCENT) {
            return "over";
        }
        return percent >= CONTEXT_WARN_PERCENT
                ? "warn"
                : null;
    }

    /// @param risk A classification of a tool on an approval card, e.g. "can modify".
    /// @return What it means, a sentence to a line, for its tooltip, or null if it is not one Stroom gives.
    static String riskExplanation(final String risk) {
        if (risk == null) {
            return null;
        }
        return switch (risk) {
            case "read-only" -> "The tool's server says it only reads.\n"
                                + "It can look things up, but not create, change or delete anything.";
            case "can modify" -> "The tool can create or change things, such as feeds, pipelines, processor "
                                 + "filters, indexes or dashboards, depending on what it is for.\n"
                                 + "Check its arguments below to see what it will do.";
            case "destructive" -> "The tool's server says it can delete or overwrite things.\n"
                                  + "That may not be possible to undo.";
            case "reaches outside Stroom" -> "The tool's server says it contacts systems outside Stroom.\n"
                                             + "What is in its arguments may leave Stroom.";
            case "untrusted server" -> "An administrator hasn't marked this tool's server as trusted.\n"
                                       + "So what it says about its tools, e.g. that one only reads, isn't "
                                       + "relied on.";
            case "after reading data" -> "This chat has read data from tools or attached tables.\n"
                                         + "That data could hold instructions planted to make the model misuse "
                                         + "this tool, or put data in its arguments.";
            default -> null;
        };
    }

    /// @param status A chat's status.
    /// @return A note on the chat's state, e.g. "needs you", or an empty string if there is nothing to say.
    static String statusNote(final AiChatStatus status) {
        if (status.isNeedsYou()) {
            return "needs you";
        } else if (status.getRunStatus() == AiChatRunStatus.QUEUED) {
            return "queued";
        } else if (status.isBusy()) {
            return "working";
        } else if (status.getRunStatus() == AiChatRunStatus.FAILED) {
            return "failed";
        } else if (status.getRunStatus() == AiChatRunStatus.INTERRUPTED) {
            return "interrupted";
        } else if (status.isUnread()) {
            return "unread";
        }
        return status.isApproveEverything()
                ? "approving everything"
                : "";
    }

    /// @param status An MCP form's status, as stored.
    /// @return What happened to the form, for its card.
    static String formStatus(final String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "ACCEPTED" -> "You answered it.";
            case "DECLINED" -> "You declined it.";
            case "CANCELLED" -> "Not answered, as the run was stopped.";
            case "EXPIRED" -> "Not answered in time.";
            default -> status;
        };
    }

    /// @param title   The chat's title, or null.
    /// @param working True if the chat is working.
    /// @return The question asked before deleting a chat.
    static String deleteChatQuestion(final String title, final boolean working) {
        final String name = title == null || title.isBlank()
                ? "this chat"
                : "“" + title + "”";
        return "Delete " + name + "? Its messages, attached data, workspace files, memory and tool approvals "
               + "are all deleted, and this cannot be undone."
               + (working
                ? " It is still working, so it will be stopped first."
                : "");
    }

    /// @param working True if the chat is working.
    /// @return The question asked before clearing a chat.
    static String clearChatQuestion(final boolean working) {
        return "Clear this chat? Its messages, attached data, workspace files and memory are deleted, so it "
               + "starts afresh. The chat, its mode and its tool approvals are kept, as is your own memory. This "
               + "can't be undone."
               + (working
                ? " It is still working, so it will be stopped first."
                : "");
    }

    /// @param count      How many chats the user has.
    /// @param anyWorking True if any of them is working.
    /// @return The question asked before deleting all the user's chats.
    static String deleteAllChatsQuestion(final int count, final boolean anyWorking) {
        return "Delete all " + (count == 1
                ? "your chat"
                : count + " of your chats") + "? Their messages, attached data, workspace files, chat memory "
               + "and tool approvals are deleted."
               + (anyWorking
                ? " Chats that are working are stopped first."
                : "")
               + " Your own memory is kept. This can't be undone.";
    }

    /// @param laterMessages How many questions and answers come after the one being edited.
    /// @param sideEffects   True if the agent used tools or changed memory after it.
    /// @return The warning given before an edited question is asked again.
    static String editWarning(final int laterMessages, final boolean sideEffects) {
        return (laterMessages == 0
                ? "Editing will ask this question again."
                : "Editing will remove " + laterMessages + " later message" + (laterMessages == 1
                        ? ""
                        : "s") + " and ask this question again.")
               + (sideEffects
                ? " Changes the agent has made with tools, or to your memory, will not be undone."
                : "");
    }
}
