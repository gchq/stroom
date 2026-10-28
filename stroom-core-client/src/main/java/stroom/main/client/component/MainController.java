/*
 * Copyright 2016 Crown Copyright
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

package stroom.main.client.component;

import stroom.content.client.component.ContentTabPaneController;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.content.client.event.RefreshCurrentContentTabEvent;
import stroom.core.client.MenuKeys;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.explorer.client.component.NavigationController;
import stroom.main.client.PageTitle;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.main.client.event.ShowMainEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.event.OpenUserTaskManagerEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItems;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.util.client.DoubleClickTester;
import stroom.widget.util.client.GlobalKeyHandler;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Shows the main screen: lays out the explorer, the document tabs and the docked panel as the events
/// ask, keeps the spinner, the page title and the theme, and opens the main menu.
public class MainController extends Controller {

    private final Main view;

    private final Timer refreshTimer;
    private boolean click;
    private final MenuItems menuItems;
    private final NavigationController navigation;
    private int taskCount;
    private boolean showingAi;
    private String baseTitle = "Stroom";
    private String selectedTabLabel;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             Its component.
    /// @param menuItems        The menu items.
    /// @param uiConfigCache    The UI's configuration.
    /// @param globalKeyHandler The global key handler.
    /// @param navigation       The navigation.
    /// @param contentTabPane   The content tab pane.
    @Inject
    public MainController(final EventBus eventBus,
                         final Main view,
                         final MenuItems menuItems,
                         final UiConfigCache uiConfigCache,
                         final GlobalKeyHandler globalKeyHandler,
                         final NavigationController navigation,
                         final ContentTabPaneController contentTabPane) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.menuItems = menuItems;
        this.navigation = navigation;
        // The content first: the view lays the explorer out beside it
        view.setContentView(own(contentTabPane));
        view.setExplorerView(own(navigation));
        view.setOnDockResize(size -> DockResizeEvent.fire(getEventBus()::fireEvent, size));
        view.getMainToolBar().setOnShowMenu(this::showMenu);
        view.getMainToolBar().setOnShowAi(() -> ShowAskStroomAiEvent.fire(this, !showingAi));

        // Handle key presses
        own(RootPanel.get().addDomHandler(globalKeyHandler::onKeyDown, KeyDownEvent.getType()));

        // Inspect the keyUp so we can catch stuff like 'shift,shift'
        own(RootPanel.get().addDomHandler(globalKeyHandler::onKeyUp, KeyUpEvent.getType()));

        listen(TaskStartEvent.getType(), event -> {
            // DebugPane.debug("taskStart:" + event.getTaskCount());
            taskCount++;
            updateSpinnerState();
        });
        listen(TaskEndEvent.getType(), event -> {
            // DebugPane.debug("taskEnd:" + event.getTaskCount());
            taskCount--;
            updateSpinnerState();
        });
        // Reflect the selected content tab in the browser page title
        listen(ContentTabSelectionChangeEvent.getType(), event -> {
            final TabData tabData = event.getTabData();
            selectedTabLabel = tabData != null
                    ? tabData.getLabel()
                    : null;
            updatePageTitle();
        });
        // F6 and Shift+F6 (unless the user has changed them) move between the main areas
        KeyBinding.addCommand(Action.FOCUS_NEXT_AREA, () -> view.focusArea(false));
        KeyBinding.addCommand(Action.FOCUS_PREVIOUS_AREA, () -> view.focusArea(true));
        own(uiConfigCache.addPropertyChangeListener(
                uiConfig -> {
                    if (uiConfig.getHtmlTitle() != null) {
                        baseTitle = uiConfig.getHtmlTitle();
                        updatePageTitle();
                    }
                    if (uiConfig.getTheme() != null) {
                        view.setBorderStyle(uiConfig.getTheme().getPageBorder());
                        view.setSelectedTabColour(uiConfig.getTheme().getSelectedTabColour());
                    }
                    view.setBanner(uiConfig.getMaintenanceMessage());
                }
        ));

        own(view.getMainToolBar().getSpinner().addClickHandler(event -> {
            if (click) {
                click = false;
                OpenUserTaskManagerEvent.fire(MainController.this);

            } else {
                final Timer clickTimer = new Timer() {
                    @Override
                    public void run() {
                        if (click) {
                            stopAutoRefresh();
                            // refresh();
                            startAutoRefresh();
                        }

                        click = false;
                    }
                };
                click = true;
                clickTimer.schedule(DoubleClickTester.DOUBLE_CLICK_PERIOD);
            }
        }));
        listen(MaximiseEvent.getType(), event -> view.maximise(event.getContent()));
        listen(DockEvent.getType(), event -> {
            final DockEvent.DockAction action = event.getAction();
            if (action == DockEvent.DockAction.DOCK) {
                view.dock(
                        event.getController().asWidget(),
                        event.getDockBehaviour().getDockLocation(),
                        event.getSize());
            } else {
                view.undock();
            }
        });
        listen(ShowAskStroomAiEvent.getType(), event -> {
            showingAi = event.isShow();
            view.getMainToolBar().getShowAi().setState(showingAi);
        });

        refreshTimer = new Timer() {
            @Override
            public void run() {
                RefreshCurrentContentTabEvent.fire(MainController.this);
            }
        };

        // Start the auto refresh timer.
        startAutoRefresh();
        onDispose(this::stopAutoRefresh);
    }

    /// Shows the explorer, once the user has logged in, and the document asked for, if any.
    ///
    /// @param event The event.
    public void onShowMain(final ShowMainEvent event) {
        navigation.onShowMain(event);
    }

    private void updatePageTitle() {
        Window.setTitle(PageTitle.build(baseTitle, selectedTabLabel));
    }

    private void updateSpinnerState() {
        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        // Always try and start spinner even if it might be spinning
        // already.
        final MainToolbar mainToolbar = view.getMainToolBar();
        if (mainToolbar != null) {
            final TaskSpinner spinner = mainToolbar.getSpinner();
            if (spinner != null) {
                if (taskCount > 0) {
                    spinner.start();
                } else if (taskCount == 0) {
                    // Only stop spinner if we are no longer executing any tasks.
                    spinner.stop();
                }
            }
        }
    }

    // The main menu, below its button
    private void showMenu(final Element target) {
        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteRight(),
                target.getAbsoluteBottom());
        showMenuItems(
                popupPosition,
                target);
    }

    // Shows the main menu, beside its button
    private void showMenuItems(final PopupPosition popupPosition,
                              final Element autoHidePartner) {
        // Clear the current menus.
        menuItems.clear();
        // Tell all plugins to add new menu items.
        BeforeRevealMenubarEvent.fire(this, menuItems);
        final List<Item> items = menuItems.getMenuItems(MenuKeys.MAIN_MENU);
        if (items != null && !items.isEmpty()) {
            ShowMenuEvent
                    .builder()
                    // Named here, as the button that opens it is titled "Show Main Menu"
                    .name("Main")
                    .items(items)
                    .popupPosition(popupPosition)
                    .opener(autoHidePartner)
                    .fire(this);
        }
    }

    private void startAutoRefresh() {
        refreshTimer.scheduleRepeating(30000); // 30 second default.
    }

    private void stopAutoRefresh() {
        refreshTimer.cancel();
    }
}
