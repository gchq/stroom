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

package stroom.gwt.workbench.client.app.screen;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.gwt.workbench.client.app.rest.FixtureDispatcher;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.widget.popup.client.presenter.PopupManager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;

import java.util.Objects;

/// Everything a real Stroom screen (presenter and view) needs to run in a story with fake data:
///
/// * an [EventBus] and Stroom's real [RestFactory], from a minimal [ScreenGinjector];
/// * a [FixtureDispatcher] that answers the screen's REST requests from the story's
///   [RestFixtures], so no server is needed;
/// * a [PopupManager], so that dialogs shown with `ShowPopupEvent` appear, on the page's body
///   as in Stroom, where play functions find them with `play.screen()`;
/// * spies on the requests made, the alerts shown and any events the story spies on, which play
///   functions check with `play.expect(play.spy(name))`.
///
/// The story creates its presenter with `new`, passing it the harness's event bus and REST
/// factory, then returns [#asWidget()], e.g.
/// ```
/// final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
/// final AboutPresenter presenter = new AboutPresenter(harness.getEventBus(), view, null,
///         harness.getRestFactory(), uiConfigCache, dateTimeFormatter);
/// presenter.show();
/// return harness.asWidget();
/// ```
public final class ScreenHarness {

    /// The name of the spy that records each REST request.
    public static final String REQUEST_SPY = "request";
    /// The name of the spy that records each alert (error/warning/info message) the screen shows.
    public static final String ALERT_SPY = "alert";

    private final StoryContext context;
    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final FlowPanel host = new FlowPanel();

    private ScreenHarness(final StoryContext context, final RestFixtures fixtures) {
        this.context = Objects.requireNonNull(context);
        final ScreenGinjector injector = GWT.create(ScreenGinjector.class);
        eventBus = injector.getEventBus();
        // Creating the REST factory sets RestyGWT's (static) service root and dispatcher, so
        // replace the dispatcher afterwards
        restFactory = injector.getRestFactory();
        Defaults.setDispatcher(new FixtureDispatcher(fixtures, this::onRequest));

        new PopupManager(eventBus);
        // Nothing shows alerts in a story, so record them instead
        eventBus.addHandler(AlertEvent.getType(), event ->
                spy(ALERT_SPY, event.getLevel() + ": " + (event.getMessage() != null
                        ? event.getMessage().asString()
                        : "")));
    }

    /// Creates the harness for a story. Create a new one each time the story renders.
    ///
    /// @param context  The story's context, to log the spies' calls to the Actions addon.
    /// @param fixtures The replies to the screen's REST requests.
    /// @return The harness.
    public static ScreenHarness create(final StoryContext context, final RestFixtures fixtures) {
        return new ScreenHarness(context, fixtures);
    }

    /// @return The event bus to give the screen's presenters.
    public EventBus getEventBus() {
        return eventBus;
    }

    /// @return The event bus as [HasHandlers], e.g. for firing Stroom's `XxxEvent.fire(...)`.
    public HasHandlers getHasHandlers() {
        return eventBus::fireEvent;
    }

    /// @return Stroom's real REST factory, whose requests are answered by the fixtures.
    public RestFactory getRestFactory() {
        return restFactory;
    }

    /// Records a call of a spy, which is also shown in the Actions addon, e.g. when the screen
    /// fires an event that a React story checks with a `fn()` arg.
    ///
    /// @param name   The name of the spy, e.g. `onOpenDoc`.
    /// @param detail Detail of the call, or null.
    public void spy(final String name, final String detail) {
        context.fn(name).call(detail);
    }

    /// Adds a widget to the story, e.g. a screen that is not shown in a dialog.
    ///
    /// @param widget The widget.
    public void add(final Widget widget) {
        host.add(widget);
    }

    /// @return The story's widget, which holds any widgets added. Dialogs are shown on the
    /// page's body, not in this.
    public Widget asWidget() {
        return host;
    }

    private void onRequest(final RecordedRequest request) {
        spy(REQUEST_SPY, request.describe() + (request.getBody() != null && !request.getBody().isEmpty()
                ? " " + request.getBody()
                : ""));
    }
}
