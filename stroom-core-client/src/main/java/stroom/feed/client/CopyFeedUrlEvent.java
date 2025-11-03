/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.feed.client;

import stroom.feed.client.CopyFeedUrlEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class CopyFeedUrlEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;
    private final String name;

    private CopyFeedUrlEvent(final String name) {
        this.name = name;
    }

    public static void fire(final HasHandlers handlers,
                            final String name) {
        handlers.fireEvent(new CopyFeedUrlEvent(name));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onOpen(this);
    }

    public String getName() {
        return name;
    }

    public static Builder builder(final HasHandlers handlers, final String name) {
        return new Builder(handlers, name);
    }

    // --------------------------------------------------------------------------------

    public static final class Builder {

        private final HasHandlers hasHandlers;
        private final String name;

        private Builder(final HasHandlers hasHandlers, final String name) {
            this.hasHandlers = Objects.requireNonNull(hasHandlers);
            this.name = Objects.requireNonNull(name);
        }

        public void fire() {
            hasHandlers.fireEvent(new CopyFeedUrlEvent(name));
        }
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onOpen(final CopyFeedUrlEvent event);
    }
}
