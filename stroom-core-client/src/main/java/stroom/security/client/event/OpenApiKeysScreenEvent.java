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

package stroom.security.client.event;

import stroom.security.client.event.OpenApiKeysScreenEvent.OpenApiKeysScreenHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenApiKeysScreenEvent extends GwtEvent<OpenApiKeysScreenHandler> {

    private static Type<OpenApiKeysScreenHandler> TYPE;
    private final UserRef userRef;

    private OpenApiKeysScreenEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the user on the API Keys screen
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenApiKeysScreenEvent(Objects.requireNonNull(userRef, "userRef required")));
    }

    public static Type<OpenApiKeysScreenHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenApiKeysScreenHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenApiKeysScreenHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenApiKeysEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenApiKeysScreenHandler extends EventHandler {

        void onOpen(OpenApiKeysScreenEvent event);
    }
}
