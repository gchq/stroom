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

package stroom.alert.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;

public class ConfirmEvent extends CommonAlertEvent<ConfirmEvent.Handler> {

    public static GwtEvent.Type<Handler> TYPE;
    private final ConfirmCallback callback;

    private ConfirmEvent(final SafeHtml message,
                         final SafeHtml detail,
                         final Level level,
                         final ConfirmCallback callback) {
        super(message, detail, level);
        this.callback = callback;
    }

    public static void fire(final HasHandlers handlers,
                            final SafeHtml message,
                            final SafeHtml detail,
                            final ConfirmCallback callback) {
        handlers.fireEvent(new ConfirmEvent(message, detail, Level.QUESTION, callback));
    }

    public static void fire(final HasHandlers handlers, final SafeHtml message, final ConfirmCallback callback) {
        handlers.fireEvent(new ConfirmEvent(message, null, Level.QUESTION, callback));
    }

    public static void fireWarn(final HasHandlers handlers,
                                final SafeHtml message,
                                final SafeHtml detail,
                                final ConfirmCallback callback) {
        handlers.fireEvent(new ConfirmEvent(message, detail, Level.WARN, callback));
    }

    public static void fireWarn(final HasHandlers handlers, final SafeHtml message, final ConfirmCallback callback) {
        handlers.fireEvent(new ConfirmEvent(message, null, Level.WARN, callback));
    }

    public static void fire(final HasHandlers handlers, final String message, final ConfirmCallback callback) {
        handlers.fireEvent(new ConfirmEvent(fromString(message), null, Level.QUESTION, callback));
    }

    public static void fireWarn(final HasHandlers handlers, final String message, final ConfirmCallback callback) {
        handlers.fireEvent(new ConfirmEvent(fromString(message), null, Level.WARN, callback));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new GwtEvent.Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onConfirm(this);
    }

    public ConfirmCallback getCallback() {
        return callback;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onConfirm(ConfirmEvent event);
    }
}
