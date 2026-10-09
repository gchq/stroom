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

/// The fake REST replies of the `App/AI/*` stories, answering Stroom's `AskStroomAiResource`
/// (`/ai/v1/...`).
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /ai/v1/createChat` | a new chat |
/// | `POST /ai/v1/askStroomAi` | sending a message |
/// | `POST /ai/v1/pollMessages/{chatId}` | polling (the messages after the request's last seen one) |
/// | `POST /ai/v1/getMessages/{chatId}` | a chat's messages |
/// | `POST /ai/v1/cancelProcessing/{chatId}` | Stop |
/// | `POST /ai/v1/deleteMessage/{chatId}/{messageId}` | deleting a message |
/// | `POST /ai/v1/updateChatTitle/{chatId}` | renaming a chat |
/// | `POST /ai/v1/listChats` | the conversation history |
/// | `POST /ai/v1/getDefaultConfig` | the default config |
/// | `POST /ai/v1/getAttachmentData` | an attachment's data |
/// | `POST /ai/v1/downloadChatHistory` | Download |
///
/// Other batches' stories that open the chat (e.g. a table's 'Ask Stroom AI' button) add its routes
/// with [#chatRoutes(RestFixtures.Builder)] and show it with [AskStroomAiChat].
public final class AiFixtures {

    // AskStroomAiResource.getDefaultConfig(). An empty config docks the chat into the app's main
    // layout (DockType.DOCK), which a story doesn't have, so the stories' config shows it as a
    // dialog
    public static final String CONFIG = """
            {"dockType": "DIALOG", "dockLocation": "RIGHT"}""";

    /// The chat's Run button.
    /// It has no title or accessible name (`AskStroomAiViewImpl` sets its text, which
    /// `InlineSvgButton` doesn't show), so it is found by its 'play' class.
    static final String RUN = "button.play";

    /// The Run button while the chat is working (a Stop button), with the 'stop' class.
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

    /// Adds the routes of a chat opened by another screen (e.g. by a table's 'Ask Stroom AI' button,
    /// which sends the table as the chat's context): the default config ([#CONFIG], a dialog),
    /// saving it in the user's preferences, a new chat (id 1), the question (or context) answered at
    /// once, a poll with no messages, complete, and the chat's title.
    ///
    /// @param builder The story's fixtures' builder.
    /// @return The builder.
    public static RestFixtures.Builder chatRoutes(final RestFixtures.Builder builder) {
        return builder
                .post("/ai/v1/getDefaultConfig", RestReply.json(CONFIG))
                // UserPreferencesResource.update(): the chat saves the default config as the user's
                .post("/preferences/v1", RestReply.json("true"))
                .post("/ai/v1/createChat", RestReply.json("{\"id\": 1, \"title\": \"\"}"))
                .post("/ai/v1/askStroomAi", RestReply.json("{\"message\": \"\"}"))
                .post("/ai/v1/pollMessages/1", RestReply.json("{\"newMessages\": [], \"complete\": true}"))
                .post("/ai/v1/updateChatTitle/1", RestReply.json("true"));
    }

    /// @param poll The poll replies.
    /// @return The routes every AI story needs, the question answered at once.
    static RestFixtures.Builder builder(final RestHandler poll) {
        return builder(RestReply.json("{\"message\": \"\"}"), poll);
    }

    /// A poll of a script of messages: the messages, as the server returns them, are those after
    /// the request's `lastSeenMessageId` (Stroom relies on the server not repeating messages).
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
