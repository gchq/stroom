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

import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.security.client.api.event.LogoutEvent;
import stroom.security.client.api.event.RequestLogoutEvent;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseOtherTabsEvent;
import stroom.widget.tab.client.event.RequestCloseSavedTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ContentManager implements HasHandlers {

    private final Map<TabData, CloseContentEvent.Handler> handlerMap = new HashMap<>();
    private final EventBus eventBus;

    @Inject
    public ContentManager(final EventBus eventBus) {
        this.eventBus = eventBus;

        eventBus.addHandler(RequestCloseTabEvent.getType(), event -> {
            final TabData tabData = event.getTabData();
            close(false, false, tabData);
        });
        eventBus.addHandler(RequestCloseOtherTabsEvent.getType(), event -> {
            final TabData tabData = event.getTabData();
            final TabData[] arr = handlerMap
                    .keySet()
                    .stream()
                    .filter(td -> !td.equals(tabData))
                    .collect(Collectors.toList())
                    .toArray(new TabData[0]);
            closeAll(false, false, arr);
        });
        eventBus.addHandler(
                RequestCloseAllTabsEvent.getType(),
                event -> closeAll(false, false));
        eventBus.addHandler(
                RequestCloseSavedTabsEvent.getType(),
                event -> closeAll(true, false));
        eventBus.addHandler(
                RequestLogoutEvent.getType(),
                event -> closeAll(false, true));
    }

    private void closeAll(final boolean ignoreIfDirty,
                          final boolean logoffAfterClose) {
        if (handlerMap.size() == 0) {
            // If there aren't any tabs then just try and
            // logout.
            if (logoffAfterClose) {
                LogoutEvent.fire(ContentManager.this);
            }
        } else {
            // Stick the keys in an array to prevent comod exception.
            final TabData[] arr = handlerMap.keySet().toArray(new TabData[0]);
            closeAll(ignoreIfDirty, logoffAfterClose, arr);
        }
    }

    private void closeAll(final boolean ignoreIfDirty,
                          final boolean logoffAfterClose,
                          final TabData[] arr) {
        // If there are tabs then iterate around them trying
        // to close each one.
        for (final TabData tabData : arr) {
            close(ignoreIfDirty, logoffAfterClose, tabData);
        }
    }

    private void close(final boolean ignoreIfDirty,
                       final boolean logoffAfterClose,
                       final TabData tabData) {
        final CloseContentEvent.Handler closeHandler = handlerMap.get(tabData);
        Callback callback = ok -> {
            if (ok) {
                forceClose(tabData);

                // Logoff if there are no more open tabs and we have been
                // asked
                // to logoff after close.
                if (logoffAfterClose && handlerMap.size() == 0) {
                    LogoutEvent.fire(ContentManager.this);
                }
            }
        };
        final CloseContentEvent event = new CloseContentEvent(ignoreIfDirty, callback);
        closeHandler.onCloseRequest(event);
    }

    public void forceClose(final TabData tabData) {
        CloseContentTabEvent.fire(ContentManager.this, tabData);
        handlerMap.remove(tabData);
    }

    public void open(final CloseContentEvent.Handler closeHandler,
                     final TabData tabData,
                     final Layer layer) {
        handlerMap.put(tabData, closeHandler);
        OpenContentTabEvent.fire(this, tabData, layer);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
