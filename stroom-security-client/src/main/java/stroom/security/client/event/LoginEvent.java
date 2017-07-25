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

public class LoginEvent extends GwtEvent<LoginEvent.LoginHandler> {
    private static Type<LoginHandler> TYPE;
    private final String userName;
    private final String password;

    private LoginEvent(final String userName, final String password) {
        this.userName = userName;
        this.password = password;
    }

    public static Type<LoginHandler> getType() {
        if (TYPE == null) {
            TYPE = new GwtEvent.Type<>();
        }
        return TYPE;
    }

    public static void fire(final HasHandlers source, final String userName, final String password) {
        source.fireEvent(new LoginEvent(userName, password));
    }

    @Override
    public Type<LoginHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final LoginHandler handler) {
        handler.onLogin(this);
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public interface LoginHandler extends EventHandler {
        void onLogin(LoginEvent event);
    }
}
