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

import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

public class ShowTabMenuEvent extends GwtEvent<ShowTabMenuEvent.Handler> {

    private static Type<Handler> TYPE;
    private final TabData tabData;
    private final List<TabData> tabList;
    private final PopupPosition popupPosition;

    private ShowTabMenuEvent(final TabData tabData, final List<TabData> tabList,
                             final PopupPosition popupPosition) {
        this.tabData = tabData;
        this.tabList = tabList;
        this.popupPosition = popupPosition;
    }

    public static void fire(final HasHandlers handlers,
                            final TabData tabData, final List<TabData> tabList,
                            final PopupPosition popupPosition) {
        handlers.fireEvent(new ShowTabMenuEvent(tabData,  tabList, popupPosition));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onShow(this);
    }

    public TabData getTabData() {
        return tabData;
    }

    public List<TabData> getTabList() {
        return tabList;
    }

    public PopupPosition getPopupPosition() {
        return popupPosition;
    }

    public interface Handler extends EventHandler {

        void onShow(ShowTabMenuEvent event);
    }
}
