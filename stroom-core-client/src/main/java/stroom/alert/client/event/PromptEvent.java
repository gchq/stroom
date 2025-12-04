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

public class PromptEvent extends GwtEvent<PromptEvent.Handler> {

    public static GwtEvent.Type<Handler> TYPE;
    private final String message;
    private final String initialValue;
    private final PromptCallback callback;

    private PromptEvent(final String message, final String initialValue, final PromptCallback callback) {
        this.message = message;
        this.initialValue = initialValue;
        this.callback = callback;
    }

    public static void fire(final HasHandlers handlers, final String message, final String initialValue,
                            final PromptCallback callback) {
        handlers.fireEvent(new PromptEvent(message, initialValue, callback));
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
        handler.onPrompt(this);
    }

    public String getMessage() {
        return message;
    }

    public String getInitialValue() {
        return initialValue;
    }

    public PromptCallback getCallback() {
        return callback;
    }

    public interface Handler extends EventHandler {

        void onPrompt(PromptEvent event);
    }
}
