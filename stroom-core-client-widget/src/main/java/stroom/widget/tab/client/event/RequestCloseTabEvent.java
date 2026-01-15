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

package stroom.widget.tab.client.event;

import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class RequestCloseTabEvent extends GwtEvent<RequestCloseTabEvent.Handler> {

    private static Type<Handler> TYPE;
    private final TabData tabData;
    private final boolean force;
    private final Runnable runOnClose;
    private final boolean resizeTabBar;

    private RequestCloseTabEvent(final TabData tabData, final boolean force, final Runnable runOnClose,
                                 final boolean resizeTabBar) {
        this.tabData = tabData;
        this.force = force;
        this.runOnClose = runOnClose;
        this.resizeTabBar = resizeTabBar;
    }

    public static void fire(final HasHandlers handlers, final TabData tabData) {
        handlers.fireEvent(new RequestCloseTabEvent(tabData, false, () -> {}, true));
    }

    /**
     * @param force If true the tab will be closed even if dirty.
     */
    public static void fire(final HasHandlers handlers, final TabData tabData, final boolean force) {
        handlers.fireEvent(new RequestCloseTabEvent(tabData, force, () -> {}, true));
    }

    public static void fire(final HasHandlers handlers, final TabData tabData, final Runnable runOnClose,
                            final boolean resizeTabBar) {
        handlers.fireEvent(new RequestCloseTabEvent(tabData, false, runOnClose, resizeTabBar));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public TabData getTabData() {
        return tabData;
    }

    public boolean isForce() {
        return force;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onCloseTab(this);
    }

    public Runnable runOnClose() {
        return runOnClose;
    }

    public boolean resizeTabBar() {
        return resizeTabBar;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCloseTab(RequestCloseTabEvent event);
    }
}
