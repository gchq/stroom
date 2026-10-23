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

package stroom.main.client.presenter;

import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.content.client.event.RefreshCurrentContentTabEvent;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.CorePresenter;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.ShowMainEvent;
import stroom.main.client.view.MainToolbar;
import stroom.main.client.view.Spinner;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.event.OpenUserTaskManagerEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuRequest;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.DoubleClickTester;
import stroom.widget.util.client.GlobalKeyHandler;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class MainPresenter
        extends MyPresenterWidget<MainPresenter.MainView>
        implements MainUiHandlers {

    private final Timer refreshTimer;
    private boolean click;
    private final MenuItems menuItems;
    private final NavigationPresenter navigation;
    private int taskCount;
    private boolean showingAi;
    private String baseTitle = "Stroom";
    private String selectedTabLabel;

    @Inject
    public MainPresenter(final EventBus eventBus,
                         final MainView view,
                         final MenuItems menuItems,
                         final UiConfigCache uiConfigCache,
                         final GlobalKeyHandler globalKeyHandler,
                         final NavigationPresenter navigation,
                         final ContentTabPanePresenter contentTabPane) {
        super(eventBus, view);
        this.menuItems = menuItems;
        this.navigation = navigation;
        // The content first: the view lays the explorer out beside it
        view.setContentView(own(contentTabPane).getView());
        view.setExplorerView(own(navigation).getView());
        view.getMainToolBar().setMainUiHandlers(this);

        // Handle key presses
        RootPanel.get().addDomHandler(globalKeyHandler::onKeyDown, KeyDownEvent.getType());

        // Inspect the keyUp so we can catch stuff like 'shift,shift'
        RootPanel.get().addDomHandler(globalKeyHandler::onKeyUp, KeyUpEvent.getType());

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
        own(eventBus.addHandler(ContentTabSelectionChangeEvent.getType(), event -> {
            final TabData tabData = event.getTabData();
            selectedTabLabel = tabData != null
                    ? tabData.getLabel()
                    : null;
            updatePageTitle();
        }));
        // Show the menus that widgets with no event bus ask for (e.g. a split pane's splitter)
        own(MenuRequest.relay(this));
        // F6 and Shift+F6 (unless the user has changed them) move between the main areas
        KeyBinding.addCommand(Action.FOCUS_NEXT_AREA, () -> getView().focusArea(false));
        KeyBinding.addCommand(Action.FOCUS_PREVIOUS_AREA, () -> getView().focusArea(true));
        own(uiConfigCache.addPropertyChangeListener(
                uiConfig -> {
                    if (uiConfig.getHtmlTitle() != null) {
                        baseTitle = uiConfig.getHtmlTitle();
                        updatePageTitle();
                    }
                    if (uiConfig.getTheme() != null) {
                        getView().setBorderStyle(uiConfig.getTheme().getPageBorder());
                        getView().setSelectedTabColour(uiConfig.getTheme().getSelectedTabColour());
                    }
                    getView().setBanner(uiConfig.getMaintenanceMessage());
                }
        ));

        own(view.getMainToolBar().getSpinner().addClickHandler(event -> {
            if (click) {
                click = false;
                OpenUserTaskManagerEvent.fire(MainPresenter.this);

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
        listen(MaximiseEvent.getType(), event -> view.maximise(event.getView()));
        listen(DockEvent.getType(), event -> {
            final DockEvent.DockAction action = event.getAction();
            if (action == DockEvent.DockAction.DOCK) {
                getView().dock(
                        event.getPresenter().getView().asWidget(),
                        event.getDockBehaviour().getDockLocation(),
                        event.getSize());
            } else {
                getView().undock();
            }
        });
        own(eventBus.addHandler(ShowAskStroomAiEvent.getType(), event -> {
            showingAi = event.isShow();
            getView().getMainToolBar().getShowAi().setState(showingAi);
        }));

        refreshTimer = new Timer() {
            @Override
            public void run() {
                RefreshCurrentContentTabEvent.fire(MainPresenter.this);
            }
        };

        // Start the auto refresh timer.
        startAutoRefresh();
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
        final MainToolbar mainToolbar = getView().getMainToolBar();
        if (mainToolbar != null) {
            final Spinner spinner = mainToolbar.getSpinner();
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

    @Override
    public void showMenu(final NativeEvent event, final Element target) {
        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteRight(),
                target.getAbsoluteBottom());
        showMenuItems(
                popupPosition,
                target);
    }

    public void showMenuItems(final PopupPosition popupPosition,
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

    @Override
    public void showAi(final NativeEvent event, final Element target) {
        ShowAskStroomAiEvent.fire(this, !showingAi);
    }

    private void startAutoRefresh() {
        refreshTimer.scheduleRepeating(30000); // 30 second default.
    }

    private void stopAutoRefresh() {
        refreshTimer.cancel();
    }

    public native Object getParentIframe() /*-{
        return window.parent.frameElement;
    }-*/;

    /// Shows the screen in the page, making it when it is first needed: once the user has logged
    /// in, and for a document opened in a tab when it isn't shown (e.g. from the full screen view).
    public static final class MainListener {

        @Inject
        public MainListener(final EventBus eventBus,
                         final Provider<CorePresenter> core,
                         final Provider<MainPresenter> presenter) {
            eventBus.addHandler(ShowMainEvent.getType(), event -> {
                core.get().setContent(presenter.get());
                presenter.get().onShowMain(event);
            });
            eventBus.addHandler(OpenContentTabEvent.getType(), event -> core.get().setContent(presenter.get()));
        }
    }


    // --------------------------------------------------------------------------------


    public interface MainView extends View {

        /// Shows the explorer.
        ///
        /// @param view Its view.
        void setExplorerView(View view);

        /// Shows the document tabs.
        ///
        /// @param view Its view.
        void setContentView(View view);

        MainToolbar getMainToolBar();

        void maximise(View view);

        void setBorderStyle(String style);

        void setBanner(String text);

        /// Moves the focus to the next (or previous) main area that is shown: the main toolbar,
        /// the explorer and the open content, going round.
        ///
        /// @param backwards Whether to go to the previous area.
        void focusArea(boolean backwards);

        void setSelectedTabColour(String colour);

        void dock(com.google.gwt.user.client.ui.Widget widget,
                  DockLocation dockLocation,
                  Size size);

        void undock();
    }
}
