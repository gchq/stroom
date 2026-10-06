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

package stroom.gwt.workbench.client.app.ai;

import stroom.gwt.workbench.client.app.editors.DocEditors;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestHandler;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The fake REST replies of the `App/AI/*` stories: React's `AskAiApi` seam as Stroom's
/// `AskStroomAiResource` (`/ai/v1/...`).
///
/// | React seam | Stroom endpoint |
/// |---|---|
/// | `createChat` | `POST /ai/v1/createChat` |
/// | `sendMessage` | `POST /ai/v1/askStroomAi` |
/// | `poll` | `POST /ai/v1/pollMessages/{chatId}` (the messages after the request's last seen one) |
/// | `getMessages` | `POST /ai/v1/getMessages/{chatId}` |
/// | `cancel` | `POST /ai/v1/cancelProcessing/{chatId}` |
/// | `deleteMessage` | `POST /ai/v1/deleteMessage/{chatId}/{messageId}` |
/// | `updateTitle` | `POST /ai/v1/updateChatTitle/{chatId}` |
/// | `listChats` | `POST /ai/v1/listChats` |
/// | `getDefaultConfig` | `POST /ai/v1/getDefaultConfig` |
/// | `getAttachmentData` | `POST /ai/v1/getAttachmentData` |
/// | `downloadChat` | `POST /ai/v1/downloadChatHistory` |
final class AiFixtures {

    // AskStroomAiResource.getDefaultConfig(). Differs from React: React's is empty; GWT's empty
    // config docks the chat into the app's main layout (DockType.DOCK), which a story doesn't
    // have, so the stories' config shows it as a dialog
    static final String CONFIG = """
            {"dockType": "DIALOG", "dockLocation": "RIGHT"}""";

    /// The chat's Run button.
    /// Differs from React: it has no title or accessible name (`AskStroomAiViewImpl` sets its text,
    /// which `InlineSvgButton` doesn't show), so it is found by its 'play' class.
    static final String RUN = "button.play";

    /// The Run button while the chat is working (React's 'Stop'), with the 'stop' class.
    static final String STOP = "button.stop";

    /// The user's preferences, with the workbench's (dark) theme: the chat saves its config into the
    /// preferences, which applies their theme to the page, as Stroom does.
    static final String PREFERENCES = StartupFixtures.DEFAULT_USER_PREFERENCES.replace("\"Light\"", "\"Dark\"");

    private AiFixtures() {
        // Static utility
    }

    /// @param ask  The reply to the question (`askStroomAi`), e.g. delayed to keep the chat working.
    /// @param poll The poll replies.
    /// @return The routes every AI story needs, to add a story's own to.
    static RestFixtures.Builder builder(final RestReply ask, final RestHandler poll) {
        return DocEditors.docSelectionRoutes(RestFixtures.builder())
                .post("/ai/v1/getDefaultConfig", RestReply.json(CONFIG))
                // UserPreferencesResource.update(): the chat saves the default config as the user's
                .post("/preferences/v1", RestReply.json("true"))
                .post("/ai/v1/createChat", RestReply.json("{\"id\": 1, \"title\": \"\"}"))
                .post("/ai/v1/askStroomAi", ask)
                .post("/ai/v1/pollMessages/1", poll)
                .post("/ai/v1/cancelProcessing/1", RestReply.json("true"))
                .post("/ai/v1/updateChatTitle/1", RestReply.json("true"))
                .post("/ai/v1/deleteMessage/1/9", RestReply.json("true"))
                .post("/ai/v1/deleteAllMessages/1", RestReply.json("true"));
    }

    /// @param poll The poll replies.
    /// @return The routes every AI story needs, the question answered at once.
    static RestFixtures.Builder builder(final RestHandler poll) {
        return builder(RestReply.json("{\"message\": \"\"}"), poll);
    }

    /// A poll of React's poll script: the messages, as the server returns them, are those after the
    /// request's `lastSeenMessageId` (React's client dedupes a repeated reply; GWT relies on the
    /// server not repeating messages).
    ///
    /// @param extra    Other members of the reply, e.g. `"attachments": [...]` or
    ///                 `"workingMessage": {...}`, or empty.
    /// @param complete Whether the conversation is complete.
    /// @param messages The messages' JSON objects, each with its `id`.
    /// @return The poll's handler.
    static RestHandler poll(final String extra, final boolean complete, final String... messages) {
        return request -> {
            final double lastSeen = lastSeenMessageId(request.getBody());
            final List<String> newMessages = new ArrayList<>();
            for (final String message : messages) {
                if (id(message) > lastSeen) {
                    newMessages.add(message);
                }
            }
            return RestReply.json("{\"newMessages\": [" + String.join(", ", newMessages) + "]"
                                  + (extra.isEmpty()
                    ? ""
                    : ", " + extra)
                                  + ", \"complete\": " + complete + "}");
        };
    }

    private static double lastSeenMessageId(final String body) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        final Object request = JsonValues.parse(body);
        return request instanceof final Map<?, ?> map && map.get("lastSeenMessageId") instanceof final Number number
                ? number.doubleValue()
                : 0;
    }

    private static double id(final String message) {
        final Object parsed = JsonValues.parse(message);
        return parsed instanceof final Map<?, ?> map && map.get("id") instanceof final Number number
                ? number.doubleValue()
                : 0;
    }
}
