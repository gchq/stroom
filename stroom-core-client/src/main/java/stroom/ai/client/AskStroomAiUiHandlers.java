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

import stroom.ai.shared.AskStroomAiContext;

import com.google.gwt.dom.client.Element;
import com.gwtplatform.mvp.client.UiHandlers;

public interface AskStroomAiUiHandlers extends UiHandlers {

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

    /**
     * Scrolls the conversation to the latest message, and follows new messages from then on.
     */
    void onJumpToLatest();

    /**
     * Called when the user scrolls the conversation, to stop or start following new messages.
     */
    void onConversationScrolled();
}
