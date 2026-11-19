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

import stroom.core.client.component.AppPage;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.Button;
import stroom.widget.text.client.Html;
import stroom.widget.text.client.Label;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/AppPage`: the page's root, which shows and hides the host page's loading
/// message (`#loading`, with its text in `#loadingText`) and hides its logo (`#logo`) once it shows a
/// screen. The story makes those elements as Stroom's host page has them (`app.html`).
public final class AppPageStories {

    // As in the host page, the loading message's text inside the loading message
    private static final String HOST_PAGE = "<div id=\"loading\" class=\"app-page__loading--hidden\">"
            + "<div id=\"loadingBox\"><div id=\"loadingRight\">"
            + "<div id=\"loadingTitle\">Stroom</div>"
            + "<div id=\"loadingText\">Loading Application. Please wait...</div>"
            + "</div></div></div>"
            + "<div id=\"logo\">Stroom</div>";
    private static final String MESSAGE = "Fetching permissions...";

    private AppPageStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AppPage", AppPageStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Showing a message while a task runs before sign in has finished (e.g. fetching
                // permissions) shows the loading message with it, then hides it. The host page's
                // elements are changed as elements: making them widgets (RootPanel.get) put the
                // text's root panel inside the message's, which GWT refuses where assertions are on
                .story("HostPageLoading", AppPageStories::render)
                .withPlay(AppPageStories::playHostPageLoading);
    }

    private static Widget render(final StoryContext context) {
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .withoutStartupFixtures()
                .build();
        final FlowPanel frame = new FlowPanel();
        frame.add(new Html(HOST_PAGE));

        final AppPage page = new AppPage();
        frame.add(page);
        final Button show = new Button();
        show.setText("Show Working");
        harness.addRegistration(show.addClickHandler(event -> page.showWorking(MESSAGE)));
        final Button hide = new Button();
        hide.setText("Hide Working");
        harness.addRegistration(hide.addClickHandler(event -> page.hideWorking()));
        final Button screen = new Button();
        screen.setText("Show Screen");
        harness.addRegistration(screen.addClickHandler(event -> page.setContent(new Label("The main screen"))));
        frame.add(show);
        frame.add(hide);
        frame.add(screen);
        harness.add(frame);
        return harness.asWidget();
    }

    private static void playHostPageLoading(final Play play) {
        final Query loading = play.querySelector("#loading");
        final Query loadingText = play.querySelector("#loadingText");
        final Query logo = play.querySelector("#logo");

        play.click(play.getByRole("button", "Show Working"));
        play.expect(loading).not().toHaveClass("app-page__loading--hidden");
        play.expect(loadingText).toHaveTextContent(MESSAGE);
        play.expect("the loading message isn't made a widget", () -> isWidget(loading.element().get()))
                .toEqual(false);
        play.expect("its text isn't made a widget", () -> isWidget(loadingText.element().get())).toEqual(false);

        play.click(play.getByRole("button", "Hide Working"));
        play.expect(loading).toHaveClass("app-page__loading--hidden");

        play.click(play.getByRole("button", "Show Screen"));
        play.expect(play.getByText("The main screen")).toBeInTheDocument();
        play.expect(logo).toHaveStyle("display", "none");
        play.expect("the logo isn't made a widget", () -> isWidget(logo.element().get())).toEqual(false);
    }

    // Whether an element is a widget's: an attached widget listens to its element's events, which
    // GWT records on the element
    private static native boolean isWidget(Element element) /*-{
        return element.__listener != null;
    }-*/;
}
