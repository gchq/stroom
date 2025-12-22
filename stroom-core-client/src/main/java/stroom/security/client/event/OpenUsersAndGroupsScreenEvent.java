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

import stroom.security.client.event.OpenUsersAndGroupsScreenEvent.OpenUsersAndGroupsScreenHandler;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenUsersAndGroupsScreenEvent extends GwtEvent<OpenUsersAndGroupsScreenHandler> {

    private static Type<OpenUsersAndGroupsScreenHandler> TYPE;
    private final UserRef userRef;

    private OpenUsersAndGroupsScreenEvent(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
    }

    /**
     * Open the user on the Users and Groups screen
     */
    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new OpenUsersAndGroupsScreenEvent(
                Objects.requireNonNull(userRef, "userRef required")));
    }

    public static Type<OpenUsersAndGroupsScreenHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenUsersAndGroupsScreenHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenUsersAndGroupsScreenHandler handler) {
        handler.onOpen(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "OpenUserOrGroupEvent{" +
               "userRef='" + userRef + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenUsersAndGroupsScreenHandler extends EventHandler {

        void onOpen(OpenUsersAndGroupsScreenEvent event);
    }
}
