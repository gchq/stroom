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

package stroom.about.client.event;

import stroom.about.client.event.ShowAboutEvent.ShowAboutHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowAboutEvent extends GwtEvent<ShowAboutHandler> {

    private static Type<ShowAboutHandler> TYPE;

    private ShowAboutEvent() {
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new ShowAboutEvent());
    }

    public static Type<ShowAboutHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<ShowAboutHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ShowAboutHandler showAboutHandler) {
        showAboutHandler.onShow(this);
    }


    // --------------------------------------------------------------------------------


    public interface ShowAboutHandler extends EventHandler {

        void onShow(ShowAboutEvent event);
    }
}
