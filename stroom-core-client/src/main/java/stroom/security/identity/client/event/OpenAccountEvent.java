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

package stroom.security.identity.client.event;

import stroom.security.identity.client.event.OpenAccountEvent.OpenAccountHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenAccountEvent extends GwtEvent<OpenAccountHandler> {

    private static Type<OpenAccountHandler> TYPE;
    private final String userId;

    private OpenAccountEvent(final String userId) {
        this.userId = Objects.requireNonNull(userId);
    }

    /**
     * Open the named node on the nodes screen
     */
    public static void fire(final HasHandlers handlers, final String userId) {
        handlers.fireEvent(new OpenAccountEvent(userId));
    }

    public static Type<OpenAccountHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenAccountHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenAccountHandler handler) {
        handler.onOpen(this);
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return "OpenAccountEvent{" +
               "userId='" + userId + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenAccountHandler extends EventHandler {

        void onOpen(OpenAccountEvent event);
    }
}
