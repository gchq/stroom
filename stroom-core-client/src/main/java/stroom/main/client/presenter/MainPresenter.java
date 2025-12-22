/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.content.client.event.RefreshCurrentContentTabEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.CorePresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.event.OpenUserTaskManagerEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.util.client.DoubleClickTester;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasDoubleClickHandlers;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.shared.GwtEvent.Type;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;

import java.util.List;

public class MainPresenter
        extends MyPresenter<MainPresenter.MainView, MainPresenter.MainProxy>
        implements MainUiHandlers {

    @ContentSlot
    public static final Type<RevealContentHandler<?>> EXPLORER = new Type<>();
    @ContentSlot
    public static final Type<RevealContentHandler<?>> CONTENT = new Type<>();
    private final Timer refreshTimer;
    private boolean click;
    private final MenuItems menuItems;
    private int taskCount;

    @Inject
    public MainPresenter(final EventBus eventBus,
                         final MainView view,
                         final MainProxy proxy,
                         final MenuItems menuItems,
                         final UiConfigCache uiConfigCache,
                         final GlobalKeyHandler globalKeyHandler) {
        super(eventBus, view, proxy);
        this.menuItems = menuItems;
        view.setUiHandlers(this);

        // Handle key presses
        RootPanel.get().addDomHandler(globalKeyHandler::onKeyDown, KeyDownEvent.getType());

        // Inspect the keyUp so we can catch stuff like 'shift,shift'
        RootPanel.get().addDomHandler(globalKeyHandler::onKeyUp, KeyUpEvent.getType());

        addRegisteredHandler(TaskStartEvent.getType(), event -> {
            // DebugPane.debug("taskStart:" + event.getTaskCount());
            taskCount++;
            updateSpinnerState();
        });
        addRegisteredHandler(TaskEndEvent.getType(), event -> {
            // DebugPane.debug("taskEnd:" + event.getTaskCount());
            taskCount--;
            updateSpinnerState();
        });
        registerHandler(uiConfigCache.addPropertyChangeHandler(
                event -> {
                    final ExtendedUiConfig uiConfig = event.getProperties();
                    if (uiConfig.getHtmlTitle() != null) {
                        Window.setTitle(uiConfig.getHtmlTitle());
                    }
                    if (uiConfig.getTheme() != null) {
                        getView().setBorderStyle(uiConfig.getTheme().getPageBorder());
                        getView().setSelectedTabColour(uiConfig.getTheme().getSelectedTabColour());
                    }
                    getView().setBanner(uiConfig.getMaintenanceMessage());
                }
        ));

        registerHandler(view.getSpinner().addClickHandler(event -> {
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
        addRegisteredHandler(MaximiseEvent.getType(), event -> view.maximise(event.getView()));

        refreshTimer = new Timer() {
            @Override
            public void run() {
                RefreshCurrentContentTabEvent.fire(MainPresenter.this);
            }
        };

        // Start the auto refresh timer.
        startAutoRefresh();
    }

    private void updateSpinnerState() {
        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        // Always try and start spinner even if it might be spinning
        // already.
        final SpinnerDisplay spinner = getView().getSpinner();
        if (spinner != null) {
            if (taskCount > 0) {
                spinner.start();
            } else if (taskCount == 0) {
                // Only stop spinner if we are no longer executing any tasks.
                spinner.stop();
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
        if (items != null && items.size() > 0) {
            ShowMenuEvent
                    .builder()
                    .items(items)
                    .popupPosition(popupPosition)
                    .addAutoHidePartner(autoHidePartner)
                    .fire(this);
        }
    }

    private void startAutoRefresh() {
        refreshTimer.scheduleRepeating(30000); // 30 second default.
    }

    private void stopAutoRefresh() {
        refreshTimer.cancel();
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, CorePresenter.CORE, this);
        RootPanel.get("logo").setVisible(false);
    }

    public native Object getParentIframe() /*-{
        return window.parent.frameElement;
    }-*/;

    @ProxyCodeSplit
    public interface MainProxy extends Proxy<MainPresenter> {

    }


    // --------------------------------------------------------------------------------


    public interface MainView extends View, HasUiHandlers<MainUiHandlers> {

        SpinnerDisplay getSpinner();

        void maximise(View view);

        void setBorderStyle(String style);

        void setBanner(String text);

        void setSelectedTabColour(String colour);
    }


    // --------------------------------------------------------------------------------


    public interface SpinnerDisplay extends HasClickHandlers, HasDoubleClickHandlers {

        void start();

        void stop();
    }
}
