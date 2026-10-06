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
import stroom.data.client.event.ShowAskStroomAiEvent;
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
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.main.client.presenter.MainUiHandlers;
import stroom.main.client.view.MainToolbar;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/AI/AskStroomAiDialog` in the React Storybook, showing Stroom's real
/// [AskStroomAiPresenter] as the 'Ask Stroom AI' dialog, opened by `ShowAskStroomAiEvent` as the
/// app's toolbar opens it, with fake REST replies ([AiFixtures]): a reply with an attachment, two
/// past chats (`listChats`, filtered by the request's filter), a past chat's messages
/// (`getMessages`), the attachment's data (`getAttachmentData`) and the chat's download
/// (`downloadChatHistory`, recorded by `DOWNLOAD_SPY`).
///
/// `ToolbarEntryPoint` shows Stroom's `MainToolbar` (the app's top bar) with its AI toggle wired
/// as `MainPresenter` wires it.
public final class AskStroomAiDialogStories {

    private static final String CAPTION = "Ask Stroom AI";

    // React's poll: a reply and a 3 row attachment
    private static final String REPLY = """
            {"id": 10, "chatId": 1, "messageType": "AI_RESPONSE", "message": "A fresh reply."}""";
    private static final String ATTACHMENT = """
            {"id": 11, "chatId": 1, "messageType": "ATTACHMENT", "message": "Result set", "attachmentId": 100}""";
    private static final String ATTACHMENTS = """
            "attachments": [{"id": 100, "chatId": 1, "status": "READY", "description": "Result set",
              "rowCount": 3, "truncated": false}]""";

    // React's HISTORY
    private static final String OLD_CHAT = """
            {"id": 5, "title": "Old chat about feeds", "updateTimeMs": 1699996400000}""";
    private static final String PIPELINE_CHAT = """
            {"id": 6, "title": "Pipeline errors", "updateTimeMs": 1699913600000}""";

    private static final String CHATS = """
            {"values": [CHATS], "pageResponse": {"offset": 0, "length": COUNT, "total": COUNT, "exact": true}}""";

    // React's LOADED
    private static final String LOADED = """
            [{"id": 50, "chatId": 5, "messageType": "USER_MESSAGE", "message": "earlier question"},
              {"id": 51, "chatId": 5, "messageType": "AI_RESPONSE", "message": "A loaded reply about **feeds**."}]""";

    private static final String ATTACHMENT_DATA = """
            {"headers": ["Feed", "Count"], "rows": [["ALPHA", "1"], ["BETA", "2"], ["GAMMA", "3"]],
              "totalRowCount": 3, "offset": 0}""";

    private static final RestFixtures FIXTURES = AiFixtures.builder(
                    AiFixtures.poll(ATTACHMENTS, true, REPLY, ATTACHMENT))
            .route(RequestMatcher.post("/ai/v1/listChats").withJsonBodyContaining("{\"filter\": \"pipeline\"}"),
                    RestReply.json(chats(PIPELINE_CHAT)))
            .post("/ai/v1/listChats", RestReply.json(chats(OLD_CHAT + ", " + PIPELINE_CHAT)))
            .post("/ai/v1/getMessages/5", RestReply.json(LOADED))
            .post("/ai/v1/getChat/5", RestReply.json(OLD_CHAT))
            .post("/ai/v1/pollMessages/5", RestReply.json("{\"complete\": true}"))
            .post("/ai/v1/getAttachmentData", RestReply.json(ATTACHMENT_DATA))
            .post("/ai/v1/downloadChatHistory", RestReply.json(
                    "{\"resourceKey\": {\"key\": \"k1\", \"name\": \"chat.md\"}, \"messageList\": []}"))
            .post("/ai/v1/setDefaultAskStroomAIConfig", RestReply.json("true"))
            .build();

    private AskStroomAiDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/AI/AskStroomAiDialog", AskStroomAiDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The dialog shows the chat; a message gets a reply
                .story("Default", AskStroomAiDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(screen.findByText(CAPTION, StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    send(screen, "hi there");
                    play.waitFor(() -> play.expect(screen.getByText("A fresh reply.")).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // History: the past chats, filtered; selecting one and OK loads its messages
                .story("OpenFromHistory", AskStroomAiDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText(CAPTION, StroomDom.DIALOG_TITLE);
                    play.click(screen.getByTitle("Conversation History"));
                    play.waitFor(() -> play.expect(screen.getByText("Chat History", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    screen.findByText("Old chat about feeds");
                    // Differs from React: the filter is Stroom's quick filter, with no label
                    final Query filter = screen.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER);
                    play.type(filter, "pipeline");
                    play.waitFor(3000, () -> play.expect(screen.queryByText("Old chat about feeds")).toBeNull());
                    play.expect(screen.getByText("Pipeline errors")).toBeInTheDocument();
                    play.clear(filter);
                    play.click(screen.findByText("Old chat about feeds"));
                    final Query ok = screen.getByRole("button", StroomDom.button("OK"));
                    play.waitFor(() -> play.expect(ok).toBeEnabled());
                    play.click(ok);
                    play.waitFor(() -> play.expect(screen.queryByText("Chat History", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("A loaded reply about")))
                            .toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // The app's toolbar AI toggle opens and closes the chat
                .story("ToolbarEntryPoint", AskStroomAiDialogStories::renderToolbar)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query aiButton = play.getByRole("button", "Ask Stroom AI");
                    play.click(aiButton);
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("How can I help?")))
                            .toBeInTheDocument());
                    // Differs from React: the toggle shows its state with the 'on' class, not
                    // aria-pressed
                    play.expect(aiButton).toHaveClass("on");
                    play.click(aiButton);
                    play.waitFor(() -> play.expect(screen.queryByText(CAPTION, StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(aiButton).not().toHaveClass("on");
                    DocEditors.expectNoProblems(play);
                })
                // An attachment's 'View data' opens its data in a paged grid
                .story("ViewAttachmentData", AskStroomAiDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText(CAPTION, StroomDom.DIALOG_TITLE);
                    send(screen, "attach the results");
                    play.click(screen.findByRole("button", "View data"));
                    play.waitFor(() -> play.expect(screen.getAllByText("Result set").count())
                            .toSatisfy("more than none", count -> ((Integer) count) > 0));
                    // Differs from React: the grid has no grid or columnheader roles
                    final Play grid = play.within(screen.findByText("ALPHA").closest(".dataGridWidget"));
                    play.expect(grid.getByText("Feed")).toBeInTheDocument();
                    play.expect(grid.getByText("ALPHA")).toBeInTheDocument();
                    // The pager's range is separate labels (with no-break spaces), so its text is
                    // normalised
                    final Value<String> pager = screen.querySelector(".pager").textContent();
                    play.expect("the pager's text", () -> pager.get().replaceAll("[\\s\u00a0]+", " "))
                            .toMatch("1 to 3 of 3");
                    DocEditors.expectNoProblems(play);
                })
                // Download: the options dialog, whose Download downloads the chat
                .story("DownloadChat", AskStroomAiDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText(CAPTION, StroomDom.DIALOG_TITLE);
                    send(screen, "anything");
                    play.waitFor(() -> play.expect(screen.getByText("A fresh reply.")).toBeInTheDocument());
                    // Download is enabled for the chat created by sending the first message (it once
                    // stayed disabled: only onNewChat and loadChat enabled it)
                    play.waitFor(() -> play.expect(screen.getByTitle("Download")).toBeEnabled());
                    play.click(screen.getByTitle("Download"));
                    play.waitFor(() -> play.expect(screen.getByText("Download Options", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    play.expect(screen.getByText(TextMatch.containing("Include data contexts"))).toBeInTheDocument();
                    final Play dialog = play.within(screen.getByText("Download Options", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    // Differs from React: the dialog's button is Stroom's OK, not 'Download'
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Download Options", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.DOWNLOAD_SPY)).toHaveBeenCalled());
                    DocEditors.expectNoProblems(play);
                })
                // Configure: the tabbed configuration dialog (General, Table Analysis); OK closes it
                .story("Configure", AskStroomAiDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText(CAPTION, StroomDom.DIALOG_TITLE);
                    play.click(screen.getByTitle("Configure"));
                    play.waitFor(() -> play.expect(screen.getByText("Configure Ask Stroom AI", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    play.expect(screen.getByText("Chat System Prompt", "label")).toBeInTheDocument();
                    play.expect(screen.getByRole("button", StroomDom.button("Set As Default"))).toBeInTheDocument();
                    play.click(screen.getByText("Table Analysis", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(screen.getByText("Max Total Rows", "label")).toBeInTheDocument());
                    play.type(screen.querySelector("#tableQueryUserPrompt"), "Analyse {{table}}");
                    final Play dialog = play.within(screen.getByText("Configure Ask Stroom AI",
                            StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Configure Ask Stroom AI",
                            StroomDom.DIALOG_TITLE)).toBeNull());
                    DocEditors.expectNoProblems(play);
                });
    }

    // Types a message and presses Run.
    // Differs from React: the message box has no 'Message' label; it is found by its placeholder
    private static void send(final Play screen, final String message) {
        screen.type(screen.findByPlaceholderText("How can I help?"), message);
        screen.click(screen.querySelector(AiFixtures.RUN));
    }

    private static String chats(final String chats) {
        final int count = chats.split("\"id\"").length - 1;
        return CHATS.replace("CHATS", chats).replace("COUNT", String.valueOf(count));
    }

    // As the AI toolbar button does: the presenter handles ShowAskStroomAiEvent (its proxy event)
    private static AskStroomAiPresenter showPresenter(final ScreenHarness harness,
                                                      final EditorsScreenGinjector injector) {
        final AskStroomAiPresenter presenter = injector.getAskStroomAiPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ShowAskStroomAiEvent.getType(), presenter));
        harness.unbindOnCleanUp(presenter);
        return presenter;
    }

    private static Widget render(final StoryContext context) {
        return DocEditors.render(context, FIXTURES, false,
                builder -> builder.startup(startup -> startup.userPreferences(AiFixtures.PREFERENCES)),
                AskStroomAiDialogStories::showDialog);
    }

    private static void showDialog(final ScreenHarness harness, final EditorsScreenGinjector injector) {
        final AskStroomAiPresenter presenter = showPresenter(harness, injector);
        harness.closeOnCleanUp(presenter);
        ShowAskStroomAiEvent.fire(harness.getHasHandlers(), true);
    }

    private static Widget renderToolbar(final StoryContext context) {
        return DocEditors.render(context, FIXTURES, false,
                builder -> builder.startup(startup -> startup.userPreferences(AiFixtures.PREFERENCES)),
                AskStroomAiDialogStories::showToolbar);
    }

    // As MainPresenter wires the toolbar's AI toggle
    private static void showToolbar(final ScreenHarness harness, final EditorsScreenGinjector injector) {
        showPresenter(harness, injector);
        final MainToolbar toolbar = new MainToolbar();
        final boolean[] showing = {false};
        harness.addRegistration(harness.getEventBus().addHandler(ShowAskStroomAiEvent.getType(), event -> {
            showing[0] = event.isShow();
            toolbar.getShowAi().setState(showing[0]);
        }));
        toolbar.setMainUiHandlers(new MainUiHandlers() {
            @Override
            public void showMenu(final NativeEvent event, final Element target) {
                // No main menu in a story
            }

            @Override
            public void showAi(final NativeEvent event, final Element target) {
                ShowAskStroomAiEvent.fire(harness.getHasHandlers(), !showing[0]);
            }
        });
        harness.add(toolbar);
    }
}
