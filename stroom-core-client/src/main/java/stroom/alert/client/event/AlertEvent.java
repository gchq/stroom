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

import stroom.util.shared.EntityServiceException;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.io.PrintStream;

public class AlertEvent extends CommonAlertEvent<AlertEvent.Handler> {

    public static GwtEvent.Type<Handler> TYPE;
    private final AlertCallback callback;

    private AlertEvent(final SafeHtml message,
                       final SafeHtml detail,
                       final Level level,
                       final AlertCallback callback) {
        super(message, detail, level);
        this.callback = callback;
    }

    public static void fireInfo(final HasHandlers handlers,
                                final String message,
                                final String detail,
                                final AlertCallback callback) {
        handlers.fireEvent(new AlertEvent(fromString(message), fromString(detail), Level.INFO, callback));
    }

    public static void fireInfo(final HasHandlers handlers,
                                final String message,
                                final AlertCallback callback) {
        fireInfo(handlers, message, null, callback);
    }

    public static void fireWarn(final HasHandlers handlers,
                                final String message,
                                final String detail,
                                final AlertCallback callback) {
        handlers.fireEvent(new AlertEvent(fromString(message), fromString(detail), Level.WARN, callback));
    }

    public static void fireWarn(final HasHandlers handlers,
                                final String message,
                                final AlertCallback callback) {
        fireWarn(handlers, message, null, callback);
    }

    public static void fireErrorFromException(final HasHandlers handlers,
                                              final Throwable throwable,
                                              final AlertCallback callback) {
        String message = throwable.getMessage();
        if (message == null || message.length() <= 1) {
            message = throwable.getClass().getName();
        }
        fireErrorFromException(handlers, message, throwable, callback);
    }

    public static void fireErrorFromException(final HasHandlers handlers,
                                              final String message,
                                              final Throwable throwable,
                                              final AlertCallback callback) {
        final StringBuilder detail = new StringBuilder();

        if (throwable instanceof EntityServiceException) {
            final String detailMessage = ((EntityServiceException) throwable).getDetail();
            if (detailMessage != null) {
                detail.append(detailMessage);
                detail.append("\n\n");
            }
            final String callStack = ((EntityServiceException) throwable).getCallStack();

            if (callStack != null) {
                detail.append(callStack);
                detail.append("\n\n");
            }

        } else {
            try {
                throwable.printStackTrace(new PrintStream(new GwtStringBuilderOutputStream(detail)));
            } catch (final RuntimeException e) {
                detail.append(e.getMessage());
            }
        }

        fireError(handlers, message, detail.toString(), callback);
    }

    public static void fireError(final HasHandlers handlers,
                                 final String message,
                                 final AlertCallback callback) {
        fireError(handlers, message, null, callback);
    }

    public static void fireError(final HasHandlers handlers,
                                 final String message,
                                 final String detail,
                                 final AlertCallback callback) {
        handlers.fireEvent(new AlertEvent(fromString(message), fromString(detail), Level.ERROR, callback));
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
        handler.onAlert(this);
    }

    public AlertCallback getCallback() {
        return callback;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onAlert(AlertEvent event);
    }
}
