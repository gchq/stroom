/*
 * Copyright 2016 Crown Copyright
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
import stroom.ai.shared.AskStroomAiContext;

import com.google.gwt.dom.client.Element;
import com.gwtplatform.mvp.client.UiHandlers;

public interface AskStroomAiUiHandlers extends UiHandlers {

    /// Called when the user picks a command from the list of commands, to show its reference.
    ///
    /// @param command The command.
    void onCommandPicked(AiSlashCommand command);

    /// Called when the user closes the command reference.
    void onCloseCommandReference();

    /// Called when the user clicks a suggested follow-up, to ask it.
    ///
    /// @param text The suggestion.
    void onFollowUp(String text);

    /// Called as the user types in the box for searching the list of chats.
    ///
    /// @param text What is in the box.
    void onChatSearch(String text);

    void setContext(AskStroomAiContext data);

    void onSendMessage(String message);

    void onCancelProcessing();

    void onNewChat();

    /**
     * Moves the chat into a content tab, or back into the panel.
     */
    void onToggleTab();

    /**
     * Shows the chat's menu (rename, compact, download, settings and so on) below an element.
     *
     * @param target The element to show it below.
     */
    void onShowMenu(Element target);

    /**
     * Shows the chat switcher (the user's chats, with where each has got to) below an element.
     *
     * @param target The element to show it below.
     */
    void onShowSwitcher(Element target);

    /// Closes the list of chats, going back to the chat being shown.
    void onCloseChatList();

    /**
     * Scrolls the conversation to the latest message, and follows new messages from then on.
     */
    void onJumpToLatest();

    /**
     * Called when the user scrolls the conversation, to stop or start following new messages.
     */
    void onConversationScrolled();

    /// Called when the conversation's view changes size, e.g. as the task list above the input grows, so the latest
    /// message can be kept in view while the conversation is being followed.
    void onConversationResized();

    /// The user chose how the agent works in the chat.
    void onAgentModeChange(AiAgentMode mode);

    /// The user clicked the auto-approve button.
    void onToggleAutoApprove();

    /**
     * Shows how the chat's context window is used, with a way to compact it, below or above an element.
     *
     * @param target The context meter.
     */
    void onShowContextMeter(Element target);

    /**
     * Called as the user types, e.g. to suggest commands when they start with "/".
     *
     * @param text What is in the input box.
     */
    void onInputChanged(String text);
}
