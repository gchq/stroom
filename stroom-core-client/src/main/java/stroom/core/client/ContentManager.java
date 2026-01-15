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

package stroom.core.client;

import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.MoveContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.security.client.api.event.LogoutEvent;
import stroom.security.client.api.event.RequestLogoutEvent;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseOtherTabsEvent;
import stroom.widget.tab.client.event.RequestCloseSavedTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.event.RequestCloseTabsEvent;
import stroom.widget.tab.client.event.RequestMoveTabEvent;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ContentManager implements HasHandlers {

    private final Map<TabData, CloseContentEvent.Handler> handlerMap = new HashMap<>();
    private final EventBus eventBus;

    @Inject
    public ContentManager(final EventBus eventBus) {
        this.eventBus = eventBus;

        eventBus.addHandler(RequestCloseTabEvent.getType(), event -> {
            final TabData tabData = event.getTabData();
            final DirtyMode dirtyMode = event.isForce()
                    ? DirtyMode.FORCE
                    : DirtyMode.CONFIRM_DIRTY;
            close(dirtyMode, false, tabData, event.resizeTabBar(), event.runOnClose());
        });

        eventBus.addHandler(RequestCloseOtherTabsEvent.getType(), event -> {
            final TabData tabData = event.getTabData();
            final TabData[] arr = handlerMap
                    .keySet()
                    .stream()
                    .filter(td -> !td.equals(tabData))
                    .toArray(TabData[]::new);
            closeAll(DirtyMode.CONFIRM_DIRTY, false, arr);
        });

        eventBus.addHandler(RequestCloseTabsEvent.getType(),
                event -> closeAll(DirtyMode.CONFIRM_DIRTY, false, event.getTabList()));

        eventBus.addHandler(RequestMoveTabEvent.getType(),
                event -> moveTab(event.getTabData(), event.getTabPos()));

        eventBus.addHandler(
                RequestCloseAllTabsEvent.getType(),
                event -> closeAll(DirtyMode.CONFIRM_DIRTY, false));

        eventBus.addHandler(
                RequestCloseSavedTabsEvent.getType(),
                event -> closeAll(DirtyMode.SKIP_DIRTY, false));

        eventBus.addHandler(
                RequestLogoutEvent.getType(),
                event -> closeAll(DirtyMode.CONFIRM_DIRTY, true));
    }

    private void closeAll(final DirtyMode dirtyMode,
                          final boolean logoffAfterClose) {
        if (handlerMap.isEmpty()) {
            // If there aren't any tabs then just try and
            // logout.
            if (logoffAfterClose) {
                LogoutEvent.fire(ContentManager.this);
            }
        } else {
            // Stick the keys in an array to prevent comod exception.
            final TabData[] arr = handlerMap.keySet().toArray(new TabData[0]);
            closeAll(dirtyMode, logoffAfterClose, arr);
        }
    }

    private void closeAll(final DirtyMode dirtyMode,
                          final boolean logoffAfterClose,
                          final TabData[] arr) {
        // If there are tabs then iterate around them trying
        // to close each one.
        for (final TabData tabData : arr) {
            close(dirtyMode, logoffAfterClose, tabData, true, null);
        }
    }

    private void close(final DirtyMode dirtyMode,
                       final boolean logoffAfterClose,
                       final TabData tabData,
                       final boolean resizeTabBar,
                       final Runnable onClose) {
        final CloseContentEvent.Handler closeHandler = handlerMap.get(tabData);
        if (closeHandler != null) {
            final Callback callback = ok -> {
                if (ok) {
                    forceClose(tabData, resizeTabBar);

                    if (onClose != null) {
                        onClose.run();
                    }

                    // Logoff if there are no more open tabs and we have been
                    // asked
                    // to logoff after close.
                    if (logoffAfterClose && handlerMap.isEmpty()) {
                        LogoutEvent.fire(ContentManager.this);
                    }
                }
            };
            final CloseContentEvent event = new CloseContentEvent(dirtyMode, callback);
            closeHandler.onCloseRequest(event);
        } else {
            GWT.log("No close handler for " + tabData.getType() + " - " + tabData.getLabel());
        }
    }

    public void moveTab(final TabData tabData, final int tabPos) {
        MoveContentTabEvent.fire(this, tabData, tabPos);
    }

    public void forceClose(final TabData tabData, final boolean resizeTabBar) {
        CloseContentTabEvent.fire(ContentManager.this, tabData, resizeTabBar);
        handlerMap.remove(tabData);
    }

    public void open(final CloseContentEvent.Handler closeHandler,
                     final TabData tabData,
                     final Layer layer) {
        open(closeHandler, tabData, layer, null, null);
    }

    public void open(final CloseContentEvent.Handler closeHandler,
                     final TabData tabData,
                     final Layer layer,
                     final MyPresenterWidget<?> presenter,
                     final Consumer<MyPresenterWidget<?>> callbackOnOpen) {
        handlerMap.put(tabData, closeHandler);
        OpenContentTabEvent.fire(this, tabData, layer, presenter, callbackOnOpen);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
