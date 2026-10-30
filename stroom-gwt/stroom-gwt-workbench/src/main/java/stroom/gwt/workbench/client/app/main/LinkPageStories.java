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

package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.iframe.client.component.IFrameContentController;
import stroom.widget.hyperlink.client.HyperlinkEvent;
import stroom.widget.hyperlink.client.LinkValue;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/LinkPage`: the page a link opens in Stroom, as a dialog (a link of type
/// `dialog`, which `HyperlinkOpener` shows in an `IFrameController`) or as a tab
/// ([IFrameContentController], for a link of type `tab`). The page is `about:blank`, so the frame
/// shows nothing and the link's own title names it.
public final class LinkPageStories {

    private static final String TITLE = "Help page";

    private LinkPageStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/LinkPage", LinkPageStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A dialog link opens the page in a dialog named by the link's title; Close closes it
                .story("Dialog", LinkPageStories::renderDialog)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText(TITLE, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.expect(dialog.querySelector("iframe")).toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText(TITLE, StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // A tab link's page, as its tab shows it: the frame is on the page's body, over the
                // tab, so that it isn't reloaded each time the tab is shown
                .story("Tab", LinkPageStories::renderTab)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.screen().querySelector("iframe.iframe-content__frame"))
                            .toBeInTheDocument());
                    // Named, as the tab is, for screen readers (it once had no name)
                    play.expect(play.screen().querySelector("iframe.iframe-content__frame"))
                            .toHaveAttribute("title", TITLE);
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget renderDialog(final StoryContext context) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .injector(injector)
                .build();
        // Stroom's handler of links, as the app makes it at start up
        injector.getHyperlinkOpener();
        harness.afterStartUp(() -> HyperlinkEvent.fire(harness.getHasHandlers(), LinkValue.builder()
                .text("Help")
                .href("about:blank")
                .type("dialog|" + TITLE)
                .build(), null));
        return harness.asWidget();
    }

    private static Widget renderTab(final StoryContext context) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .injector(injector)
                .build();
        harness.afterStartUp(() -> {
            // As HyperlinkOpener opens a tab link's page. Shown as a page: the tab itself
            // holds nothing to focus (its frame is on the page's body), so the runner's check that a
            // tab takes the focus doesn't apply
            final IFrameContentController tab = harness.showPage(injector.getIFrameContentController());
            tab.setUrl("about:blank");
            tab.setCustomTitle(TITLE);
        });
        return harness.asWidget();
    }
}
