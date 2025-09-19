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

package stroom.security.client.api.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class LogoutEvent extends GwtEvent<LogoutEvent.LogoutHandler> {

    private static Type<LogoutHandler> TYPE;

    private LogoutEvent() {
        // Private constructor.
    }

    public static Type<LogoutHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new LogoutEvent());
    }

    @Override
    public Type<LogoutHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final LogoutHandler handler) {
        handler.onLogout(this);
    }


    // --------------------------------------------------------------------------------


    public interface LogoutHandler extends EventHandler {

        void onLogout(LogoutEvent event);
    }
}
