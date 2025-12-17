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

public class SelectContentTabEvent extends GwtEvent<SelectContentTabEvent.SelectContentTabHandler> {

    private static Type<SelectContentTabHandler> TYPE;
    private final TabData tabData;

    private SelectContentTabEvent(final TabData tabData) {
        this.tabData = tabData;
    }

    public static void fire(final HasHandlers handlers, final TabData tabData) {
        handlers.fireEvent(new SelectContentTabEvent(tabData));
    }

    public static Type<SelectContentTabHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<SelectContentTabHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final SelectContentTabHandler handler) {
        handler.onSelect(this);
    }

    public TabData getTabData() {
        return tabData;
    }


    // --------------------------------------------------------------------------------


    public interface SelectContentTabHandler extends EventHandler {

        void onSelect(SelectContentTabEvent event);
    }
}
