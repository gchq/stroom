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

package stroom.gwt.workbench.client.app.dashboard;

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.client.component.QueryInfoPrompt;
import stroom.query.client.component.QueryInfoPromptController;

import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/QueryInfoDialog`, showing Stroom's real [QueryInfoPromptController], the
/// dialog that asks for a query's info before it is run (when the site asks for it), as
/// `QueryInfo` shows it: with the site's title and pattern, and the info last given.
///
/// A spy records the info given on OK, which runs the query in Stroom.
public final class QueryInfoPromptStories {

    /// The name of the spy recording the info given.
    static final String ON_OK = "onOk";

    private static final String TITLE = "Why are you running this query?";

    private QueryInfoPromptStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/QueryInfoDialog", QueryInfoPromptStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The info last given is shown again, focused; OK gives it
                .story("Default", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query info = screen.findByRole("textbox", "Query Info");
                    play.expect(screen.getByText(TITLE, StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(info).toHaveValue("Ticket 1");
                    play.waitFor(() -> play.expect(info).toHaveFocus());
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_OK)).toHaveBeenCalledWith("Ticket 1"));
                    play.waitFor(() -> play.expect(screen.queryByText(TITLE, StroomDom.DIALOG_TITLE)).toBeNull());
                    expectNoProblems(play);
                })
                // Cancel gives nothing, so the query isn't run
                .story("Cancel", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.type(screen.findByRole("textbox", "Query Info"), " more");
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(TITLE, StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.spy(ON_OK)).not().toHaveBeenCalled();
                    expectNoProblems(play);
                })
                // Info that doesn't match the site's pattern is refused, and the dialog stays open
                // to correct it
                .story("NotValid", context -> render(context, "TICKET-\\d+"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query info = screen.findByRole("textbox", "Query Info");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("The text entered is not valid")));
                    play.click(screen.within(screen.findByText("The text entered is not valid")
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("Close")));
                    play.expect(screen.getByText(TITLE, StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ON_OK)).not().toHaveBeenCalled();
                    play.clear(info);
                    play.type(info, "TICKET-12");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_OK)).toHaveBeenCalledWith("TICKET-12"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final String validationRegex) {
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.none())
                .realAlerts()
                .build();
        final Spy onOk = harness.fn(ON_OK);
        new QueryInfoPromptController(harness.getEventBus(), new QueryInfoPrompt())
                .show(harness.getAppRoot(), TITLE, validationRegex, "Ticket 1", onOk::call);
        return harness.asWidget();
    }
}
