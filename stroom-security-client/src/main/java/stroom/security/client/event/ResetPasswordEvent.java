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
import stroom.security.client.event.ResetPasswordEvent.ResetPasswordHandler;
import stroom.security.shared.UserRef;

public class ResetPasswordEvent extends GwtEvent<ResetPasswordHandler> {
    private static Type<ResetPasswordHandler> TYPE;
    private final UserRef userRef;

    private ResetPasswordEvent(final UserRef userRef) {
        this.userRef = userRef;
    }

    public static void fire(final HasHandlers handlers, final UserRef userRef) {
        handlers.fireEvent(new ResetPasswordEvent(userRef));
    }

    public static Type<ResetPasswordHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<ResetPasswordHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ResetPasswordHandler handler) {
        handler.onResetPassword(this);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public interface ResetPasswordHandler extends EventHandler {
        void onResetPassword(ResetPasswordEvent event);
    }
}
