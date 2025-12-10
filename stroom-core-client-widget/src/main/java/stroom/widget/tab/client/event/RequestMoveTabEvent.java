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

public class RequestMoveTabEvent extends GwtEvent<RequestMoveTabEvent.Handler> {

    private static Type<Handler> TYPE;
    private final TabData tabData;
    private final int tabPos;

    private RequestMoveTabEvent(final TabData tabData, final int tabPos) {
        this.tabData = tabData;
        this.tabPos = tabPos;
    }

    public static void fire(final HasHandlers handlers, final TabData tabData, final int tabPos) {
        handlers.fireEvent(new RequestMoveTabEvent(tabData, tabPos));
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

    public int getTabPos() {
        return tabPos;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onMoveTabs(this);
    }

    public interface Handler extends EventHandler {

        void onMoveTabs(RequestMoveTabEvent event);
    }
}
