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

public class EmailResetPasswordEvent extends GwtEvent<EmailResetPasswordEvent.EmailResetPasswordHandler> {
    private static Type<EmailResetPasswordHandler> TYPE;
    private final String userName;

    private EmailResetPasswordEvent(final String userName) {
        this.userName = userName;
    }

    public static Type<EmailResetPasswordHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public static <I> void fire(final HasHandlers source, final String userName) {
        source.fireEvent(new EmailResetPasswordEvent(userName));
    }

    @Override
    public Type<EmailResetPasswordHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final EmailResetPasswordHandler handler) {
        handler.onResetPassword(this);
    }

    public String getUserName() {
        return userName;
    }

    public interface EmailResetPasswordHandler extends EventHandler {
        void onResetPassword(EmailResetPasswordEvent event);
    }
}
