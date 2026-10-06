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
import stroom.alert.client.presenter.CommonAlertPresenter;
import stroom.alert.client.view.CommonAlertViewImpl;
import stroom.event.client.StaticEventBus;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.widget.help.client.presenter.HelpManager;
import stroom.widget.menu.client.presenter.Menu;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.menu.client.presenter.MenuViewImpl;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupManager;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.PresenterWidget;

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
    private final List<PresenterWidget<?>> openPopups = new ArrayList<>();
    private final List<Runnable> cleanUps = new ArrayList<>();
    private boolean disposed;

    private StoryPopups(final StoryContext context) {
        context.addCleanUp(this::dispose);
        eventBus.addHandler(ShowPopupEvent.getType(), this::onShow);
        eventBus.addHandler(HidePopupEvent.getType(), event -> openPopups.remove(event.getPresenterWidget()));
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

    /// Shows help popups as Stroom does: `HelpButton` fires `ShowHelpEvent` on Stroom's static
    /// event bus, which Stroom's `HelpManager` handles.
    ///
    /// @return This, for chaining.
    public StoryPopups withHelp() {
        new StaticEventBus(eventBus);
        new HelpManager(eventBus);
        return this;
    }

    /// Shows menus as Stroom does: Stroom's [Menu] handles `ShowMenuEvent`, creating a
    /// [MenuPresenter] for each menu and submenu.
    ///
    /// @return This, for chaining.
    public StoryPopups withMenus() {
        new Menu(eventBus, this::createMenuPresenter);
        return this;
    }

    /// Shows alerts and confirmations (`AlertEvent`, `ConfirmEvent`) as Stroom does, with
    /// Stroom's [AlertPlugin] and `CommonAlertPresenter`.
    ///
    /// @return This, for chaining.
    public StoryPopups withAlerts() {
        final AlertPlugin alertPlugin = new AlertPlugin(eventBus,
                new CommonAlertPresenter(eventBus, new CommonAlertViewImpl()));
        alertPlugin.bind();
        cleanUps.add(alertPlugin::unbind);
        return this;
    }

    /// Creates a menu presenter as Stroom's GIN module would.
    ///
    /// @return A new menu presenter, using this event bus.
    public MenuPresenter createMenuPresenter() {
        return new MenuPresenter(eventBus, new MenuViewImpl(), this::createMenuPresenter);
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
        final PresenterWidget<?> presenter = event.getPresenterWidget();
        // PopupManager toggles a popup that is shown twice
        if (!openPopups.remove(presenter)) {
            openPopups.add(presenter);
        }
    }

    private void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        // Hide the popups as Stroom would, most recently shown first
        final List<PresenterWidget<?>> popups = new ArrayList<>(openPopups);
        for (int i = popups.size() - 1; i >= 0; i--) {
            HidePopupEvent.builder(popups.get(i)).autoClose(true).ok(false).fire();
        }
        for (int i = cleanUps.size() - 1; i >= 0; i--) {
            cleanUps.get(i).run();
        }
        cleanUps.clear();
    }
}
