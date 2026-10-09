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

import stroom.ai.client.AskStroomAiPresenter;
import stroom.gwt.workbench.client.app.editors.DocEditors;
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The `App/AI/AskAiChatPanel` stories, showing the chat panel of Stroom's real
/// [AskStroomAiPresenter] (its view, which Stroom shows docked or in the 'Ask Stroom AI' dialog) in
/// a 480 by 560 pixel box, with fake REST replies ([AiFixtures]).
///
/// Viewing an attachment opens Stroom's attachment data dialog, which the presenter opens itself;
/// the play checks the data it asks for.
public final class AskAiChatPanelStories {

    private static final String EMPTY_PROMPT = "How can I help?";

    // The conversation's messages
    private static final String MESSAGES = ".markdown-container";

    private AskAiChatPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/AI/AskAiChatPanel", AskAiChatPanelStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The empty state: the prompt and a disabled Run (no text yet)
                .story("Empty", context -> render(context, AiFixtures.builder(AiFixtures.poll("", true)).build()))
                .withPlay(play -> {
                    play.expect(play.findByText(TextMatch.containing(EMPTY_PROMPT))).toBeInTheDocument();
                    play.expect(play.querySelector(AiFixtures.RUN)).toHaveClass("disabled");
                    play.expect(play.getByTitle("New Conversation")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // Send a message; the poll returns the user's message (shown once) and a markdown
                // reply, and Run returns from its Stop state
                .story("SendAndReceive", context -> render(context, AiFixtures.builder(AiFixtures.poll("", true,
                        "{\"id\": 9, \"chatId\": 1, \"messageType\": \"USER_MESSAGE\", "
                        + "\"message\": \"What is the answer?\"}",
                        "{\"id\": 10, \"chatId\": 1, \"messageType\": \"AI_RESPONSE\", "
                        + "\"message\": \"The answer is **42**.\"}")).build()))
                .withPlay(play -> {
                    send(play, "What is the answer?");
                    play.waitFor(() -> play.expect(play.getByText("42")).toBeInTheDocument());
                    // Stroom also titles the conversation with the first message, so the message is
                    // counted in the conversation, not the whole panel
                    play.expect(play.within(play.querySelector(MESSAGES)).getAllByText("What is the answer?"))
                            .toHaveLength(1);
                    play.waitFor(() -> play.expect(play.querySelector(AiFixtures.RUN)).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // While the assistant works, Run is a Stop button, which cancels
                .story("StopWhileWorking", context -> render(context, AiFixtures.builder(
                        // The question is still being answered (the request is in flight)
                        RestReply.json("{\"message\": \"\"}").delayed(60000),
                        // Stroom shows what the server is working on from the poll's
                        // workingMessage, not from a WORKING message in its new messages
                        AiFixtures.poll("\"workingMessage\": {\"id\": 5, \"chatId\": 1, "
                                        + "\"messageType\": \"WORKING\", \"message\": \"Analysing the table...\"}",
                                false)).build()))
                .withPlay(play -> {
                    send(play, "Analyse this");
                    // The working line shows the server's message
                    play.waitFor(() -> play.expect(play.getByText("Analysing the table...")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.querySelector(AiFixtures.STOP)).toBeInTheDocument());
                    play.click(play.querySelector(AiFixtures.STOP));
                    play.waitFor(() -> play.expect(play.querySelector(AiFixtures.RUN)).toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/ai/v1/cancelProcessing/1").toSpyMatcher());
                    DocEditors.expectNoProblems(play);
                })
                // An attachment shows its status and a 'View data' button, which opens its data
                .story("AttachmentReady", context -> render(context, AiFixtures.builder(AiFixtures.poll(
                                "\"attachments\": [{\"id\": 100, \"chatId\": 1, \"status\": \"READY\", "
                                + "\"description\": \"Orders table\", \"rowCount\": 42, \"truncated\": false}]",
                                true,
                                "{\"id\": 7, \"chatId\": 1, \"messageType\": \"ATTACHMENT\", "
                                + "\"message\": \"Attached table\", \"attachmentId\": 100}"))
                        .post("/ai/v1/getAttachmentData", RestReply.json("""
                                {"headers": ["Order"], "rows": [["1"]], "totalRowCount": 1, "offset": 0}"""))
                        .build()))
                .withPlay(play -> {
                    send(play, "attach the table");
                    play.waitFor(() -> play.expect(play.getByText("Attached table")).toBeInTheDocument());
                    play.expect(play.getByText(TextMatch.containing("Ready --- 42 rows"))).toBeInTheDocument();
                    final Query view = play.getByRole("button", "View data");
                    play.expect(view).toBeEnabled();
                    play.click(view);
                    // Stroom opens the attachment's data itself, so the play checks the data it
                    // asks for
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/ai/v1/getAttachmentData")
                                    .withJsonBodyContaining("{\"attachmentId\": 100}")
                                    .toSpyMatcher()));
                    DocEditors.expectNoProblems(play);
                })
                // Deleting a message asks for confirmation first
                .story("DeleteMessageConfirm", context -> render(context, AiFixtures.builder(AiFixtures.poll("", true,
                        "{\"id\": 9, \"chatId\": 1, \"messageType\": \"USER_MESSAGE\", \"message\": \"delete me\"}",
                        "{\"id\": 10, \"chatId\": 1, \"messageType\": \"AI_RESPONSE\", \"message\": \"ok\"}"))
                        // The chat is reloaded once the message is deleted
                        .post("/ai/v1/getMessages/1", RestReply.json(
                                "[{\"id\": 10, \"chatId\": 1, \"messageType\": \"AI_RESPONSE\", \"message\": \"ok\"}]"))
                        .build()))
                .withPlay(play -> {
                    send(play, "delete me");
                    play.waitFor(() -> play.expect(play.getByText("ok")).toBeInTheDocument());
                    // The message sent is replaced by the stored one, which has a Delete button (a
                    // message sent in this session once had none: it was rendered with no id, and the
                    // poll skipped the stored copy). It is shown once
                    play.waitFor(() -> play.expect(play.getByTitle("Delete message")).toBeInTheDocument());
                    play.expect(play.within(play.querySelector(MESSAGES)).getAllByText("delete me").count())
                            .toBe(1);
                    play.click(play.getByTitle("Delete message"));
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Are you sure you want to delete this message?"))
                            .toBeInTheDocument());
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/ai/v1/deleteMessage/1/9").toSpyMatcher()));
                    DocEditors.expectNoProblems(play);
                })
                // New Conversation clears the conversation
                .story("NewChatClears", context -> render(context, AiFixtures.builder(AiFixtures.poll("", true,
                        "{\"id\": 10, \"chatId\": 1, \"messageType\": \"AI_RESPONSE\", \"message\": \"Reply one.\"}"))
                        .build()))
                .withPlay(play -> {
                    send(play, "first question");
                    play.waitFor(() -> play.expect(play.getByText("Reply one.")).toBeInTheDocument());
                    play.click(play.getByTitle("New Conversation"));
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.containing(EMPTY_PROMPT)))
                            .toBeVisible());
                    play.expect(play.queryByText("Reply one.")).toBeNull();
                    DocEditors.expectNoProblems(play);
                });
    }

    // Types a message and presses Run.
    // The message box has no label; it is found by its placeholder
    private static void send(final Play play, final String message) {
        play.type(play.findByPlaceholderText(EMPTY_PROMPT), message);
        play.click(play.querySelector(AiFixtures.RUN));
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        return DocEditors.render(context, fixtures, false,
                builder -> builder.startup(startup -> startup.userPreferences(AiFixtures.PREFERENCES)),
                AskAiChatPanelStories::showPanel);
    }

    // The chat's view in a panel box: 480 by 560 pixels, with a border
    private static void showPanel(final ScreenHarness harness, final EditorsScreenGinjector injector) {
        final AskStroomAiPresenter presenter = injector.getAskStroomAiPresenter();
        final SimplePanel box = new SimplePanel(presenter.getWidget());
        box.getElement().getStyle().setProperty("width", "480px");
        box.getElement().getStyle().setProperty("height", "560px");
        box.getElement().getStyle().setProperty("display", "flex");
        box.getElement().getStyle().setProperty("border", "1px solid var(--separator__color, #444)");
        harness.add(box);
        harness.unbindOnCleanUp(presenter);
    }
}
