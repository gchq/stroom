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

package stroom.core.client;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.security.client.event.LogoutEvent;
import stroom.security.client.event.RequestLogoutEvent;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.presenter.Layer;
import stroom.widget.tab.client.presenter.TabData;

import java.util.HashMap;
import java.util.Map;

public class ContentManager implements HasHandlers {
    private final Map<TabData, CloseHandler> handlerMap = new HashMap<>();
    private final EventBus eventBus;

    @Inject
    public ContentManager(final EventBus eventBus) {
        this.eventBus = eventBus;

        eventBus.addHandler(RequestCloseTabEvent.getType(), new RequestCloseTabEvent.Handler() {
            @Override
            public void onCloseTab(final RequestCloseTabEvent event) {
                final TabData tabData = event.getTabData();
                final CloseHandler closeHandler = handlerMap.get(tabData);
                close(closeHandler, tabData, false);
            }
        });
        eventBus.addHandler(RequestCloseAllTabsEvent.getType(), new RequestCloseAllTabsEvent.Handler() {
            @Override
            public void onCloseAllTabs(final RequestCloseAllTabsEvent event) {
                closeAll(false);
            }
        });
        eventBus.addHandler(RequestLogoutEvent.getType(), new RequestLogoutEvent.Handler() {
            @Override
            public void onLogoutRequest(final RequestLogoutEvent event) {
                closeAll(true);
            }
        });
    }

    private void closeAll(final boolean logoffAfterClose) {
        if (handlerMap.size() == 0) {
            // If there aren't any tabs then just try and
            // logout.
            if (logoffAfterClose) {
                LogoutEvent.fire(ContentManager.this);
            }
        } else {
            // Stick the keys in an array to prevent comod exception.
            TabData[] arr = new TabData[handlerMap.entrySet().size()];
            arr = handlerMap.keySet().toArray(arr);

            // If there are tabs then iterate around them trying
            // to close each one.
            for (final TabData tabData : arr) {
                final CloseHandler closeHandler = handlerMap.get(tabData);
                close(closeHandler, tabData, logoffAfterClose);
            }
        }
    }

    private void close(final CloseHandler closeHandler, final TabData tabData, final boolean logoffAfterClose) {
        closeHandler.onCloseRequest(new CloseCallback() {
            @Override
            public void closeTab(final boolean ok) {
                if (ok) {
                    forceClose(tabData);

                    // Logoff if there are no more open tabs and we have been
                    // asked
                    // to logoff after close.
                    if (logoffAfterClose && handlerMap.size() == 0) {
                        LogoutEvent.fire(ContentManager.this);
                    }
                }
            }
        });
    }

    public void forceClose(final TabData tabData) {
        CloseContentTabEvent.fire(ContentManager.this, tabData);
        handlerMap.remove(tabData);
    }

    public void open(final CloseHandler closeHandler, final TabData tabData, final Layer layer) {
        handlerMap.put(tabData, closeHandler);
        OpenContentTabEvent.fire(this, tabData, layer);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    public interface CloseCallback {
        void closeTab(boolean ok);
    }

    public interface CloseHandler {
        void onCloseRequest(CloseCallback callback);
    }
}
