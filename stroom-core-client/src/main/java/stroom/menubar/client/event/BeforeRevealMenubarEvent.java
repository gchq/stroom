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

package stroom.menubar.client.event;

import stroom.widget.menu.client.presenter.MenuItems;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class BeforeRevealMenubarEvent extends GwtEvent<BeforeRevealMenubarEvent.Handler> {

    private static Type<Handler> TYPE;
    private final MenuItems menuItems;

    private BeforeRevealMenubarEvent(final MenuItems menuItems) {
        this.menuItems = menuItems;
    }

    public static void fire(final HasHandlers handlers, final MenuItems menuItems) {
        handlers.fireEvent(new BeforeRevealMenubarEvent(menuItems));
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
        handler.onReveal(this);
    }

    public MenuItems getMenuItems() {
        return menuItems;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onReveal(BeforeRevealMenubarEvent event);
    }
}
