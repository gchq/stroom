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

package stroom.gwt.workbench.client.widgets;

import stroom.alert.client.AlertPlugin;
import stroom.alert.client.component.CommonAlert;
import stroom.alert.client.component.CommonAlertController;
import stroom.data.client.event.AskStroomAiTableRequest;
import stroom.event.client.StaticEventBus;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.hyperlink.client.OpenHyperlinkRequest;
import stroom.widget.component.client.AppRoot;
import stroom.widget.component.client.Controller;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.help.client.HelpManager;
import stroom.widget.menu.client.Menu;
import stroom.widget.menu.client.MenuController;
import stroom.widget.menu.client.MenuManager;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.popup.client.PopupManager;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.ArrayList;
import java.util.List;

/// The popup plumbing that Stroom's GIN modules set up, for widget stories that show popups
/// (dialogs, menus, help and tooltips) but make no REST calls, so don't need a `ScreenHarness`.
///
/// It creates an event bus with Stroom's [PopupManager], so popups shown with `ShowPopupEvent`
/// appear on the page's body as in Stroom, and hides those still open with Stroom's
/// `HidePopupEvent` when the story renders again (which unbinds their presenters and restores
/// the focus). Create it in `render`, each time the story renders.
public final class StoryPopups {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<Controller> openPopups = new ArrayList<>();
    private final List<Runnable> cleanUps = new ArrayList<>();
    private boolean disposed;

    private StoryPopups(final StoryContext context) {
        context.addCleanUp(this::dispose);
        eventBus.addHandler(ShowPopupEvent.getType(), this::onShow);
        eventBus.addHandler(HidePopupEvent.getType(), event -> openPopups.remove(event.getController()));
        new PopupManager(eventBus);
    }

    /// Creates the popup plumbing for a rendering of a story.
    ///
    /// @param context The story's context, to register the clean up with.
    /// @return The popup plumbing.
    public static StoryPopups create(final StoryContext context) {
        return new StoryPopups(context);
    }

    /// @return The event bus that popups are shown with.
    public EventBus getEventBus() {
        return eventBus;
    }

    /// Shows help popups as Stroom does: a `FormGroup`'s help button fires `ShowHelpEvent` on Stroom's static
    /// event bus, which Stroom's `HelpManager` handles.
    ///
    /// @return This, for chaining.
    public StoryPopups withHelp() {
        new StaticEventBus(eventBus);
        new HelpManager(eventBus);
        return this;
    }

    /// Shows menus as Stroom does: Stroom's [Menu] handles `ShowMenuEvent`, creating a
    /// [MenuController] for each menu and submenu.
    ///
    /// @return This, for chaining.
    public StoryPopups withMenus() {
        // Its menus are owned until hidden, as by Stroom's app root
        new MenuManager(eventBus, this::createMenuController, new AppRoot());
        // and the edit menu on text fields, in place of the browser's
        cleanUps.add(ContextMenuManager.install(eventBus::fireEvent));
        return this;
    }

    /// Passes on what widgets with no event bus ask of the app, as Stroom's core presenter does:
    /// the menus they ask for (e.g. a split pane's splitter's, or a data grid's context menu, with
    /// `MenuRequest`), shown with [#withMenus]'s menus, and the links they open
    /// (`OpenHyperlinkRequest`) and the tables they ask Stroom AI about (`AskStroomAiTableRequest`),
    /// fired as events on [#getEventBus()].
    ///
    /// @return This, for chaining.
    public StoryPopups withPageRequests() {
        withMenus();
        final HasHandlers handlers = event -> eventBus.fireEvent(event);
        cleanUps.add(MenuRequest.relay(handlers)::removeHandler);
        cleanUps.add(OpenHyperlinkRequest.relay(handlers)::removeHandler);
        cleanUps.add(AskStroomAiTableRequest.relay(handlers)::removeHandler);
        return this;
    }

    /// Shows alerts and confirmations (`AlertEvent`, `ConfirmEvent`) as Stroom does, with
    /// Stroom's [AlertPlugin] and `CommonAlertController`.
    ///
    /// @return This, for chaining.
    public StoryPopups withAlerts() {
        final AlertPlugin alertPlugin = new AlertPlugin(eventBus,
                () -> new CommonAlertController(eventBus, new CommonAlert()),
                new AppRoot());
        return this;
    }

    /// Creates a menu's controller as Stroom's GIN injector would.
    ///
    /// @return A new menu controller, using this event bus.
    public MenuController createMenuController() {
        return new MenuController(eventBus, new Menu(), this::createMenuController);
    }

    /// Registers something to undo when the story renders again.
    ///
    /// @param cleanUp The clean up.
    public void addCleanUp(final Runnable cleanUp) {
        cleanUps.add(cleanUp);
    }

    /// @return True once the story has rendered again and this has been disposed.
    public boolean isDisposed() {
        return disposed;
    }

    private void onShow(final ShowPopupEvent event) {
        final Controller popup = event.getController();
        // PopupManager toggles a popup that is shown twice
        if (!openPopups.remove(popup)) {
            openPopups.add(popup);
        }
    }

    private void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        // Hide the popups as Stroom would, most recently shown first
        final List<Controller> popups = new ArrayList<>(openPopups);
        for (int i = popups.size() - 1; i >= 0; i--) {
            HidePopupEvent.builder(popups.get(i)).autoClose(true).ok(false).fire();
        }
        for (int i = cleanUps.size() - 1; i >= 0; i--) {
            cleanUps.get(i).run();
        }
        cleanUps.clear();
    }
}
