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

package stroom.security.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.security.shared.UserRef;

public class ChangePasswordEvent extends GwtEvent<ChangePasswordEvent.ChangePasswordHandler> {
    private static Type<ChangePasswordHandler> TYPE;
    private final UserRef userRef;
    private final boolean logon;
    private ChangePasswordEvent(final UserRef userRef, final boolean logon) {
        this.userRef = userRef;
        this.logon = logon;
    }

    public static void fire(final HasHandlers handlers, final UserRef userRef, final boolean logon) {
        handlers.fireEvent(new ChangePasswordEvent(userRef, logon));
    }

    public static Type<ChangePasswordHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<ChangePasswordHandler>();
        }
        return TYPE;
    }

    @Override
    public Type<ChangePasswordHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ChangePasswordHandler handler) {
        handler.onChangePassword(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public boolean isLogonChange() {
        return logon;
    }

    public interface ChangePasswordHandler extends EventHandler {
        void onChangePassword(ChangePasswordEvent event);
    }
}
