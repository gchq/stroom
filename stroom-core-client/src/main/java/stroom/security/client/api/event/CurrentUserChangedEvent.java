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

package stroom.security.client.api.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class CurrentUserChangedEvent extends GwtEvent<CurrentUserChangedEvent.CurrentUserChangedHandler> {

    public static Type<CurrentUserChangedHandler> TYPE;

    public static <I> void fire(final HasHandlers source) {
        source.fireEvent(new CurrentUserChangedEvent());
    }

    public static Type<CurrentUserChangedHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<CurrentUserChangedHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final CurrentUserChangedHandler handler) {
        handler.onCurrentUserChanged(this);
    }

    public interface CurrentUserChangedHandler extends EventHandler {

        void onCurrentUserChanged(CurrentUserChangedEvent event);
    }
}
