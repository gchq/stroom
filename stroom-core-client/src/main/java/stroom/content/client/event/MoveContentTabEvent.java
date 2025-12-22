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

package stroom.content.client.event;

import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class MoveContentTabEvent extends GwtEvent<MoveContentTabEvent.MoveContentTabHandler> {

    private static Type<MoveContentTabHandler> TYPE;
    private final TabData tabData;
    private final int tabPos;

    private MoveContentTabEvent(final TabData tabData, final int tabPos) {
        this.tabData = tabData;
        this.tabPos = tabPos;
    }

    public static void fire(final HasHandlers handlers, final TabData tabData, final int tabPos) {
        handlers.fireEvent(new MoveContentTabEvent(tabData, tabPos));
    }

    public static Type<MoveContentTabHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<MoveContentTabHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final MoveContentTabHandler handler) {
        handler.onMove(this);
    }

    public TabData getTabData() {
        return tabData;
    }

    public int getTabPos() {
        return tabPos;
    }


    // --------------------------------------------------------------------------------


    public interface MoveContentTabHandler extends EventHandler {

        void onMove(MoveContentTabEvent event);
    }
}
