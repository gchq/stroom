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

package stroom.data.grid.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class DoubleClickEvent extends GwtEvent<DoubleClickEvent.Handler> {

    private static GwtEvent.Type<Handler> TYPE;

    private DoubleClickEvent() {

    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new DoubleClickEvent());
    }

    public static GwtEvent.Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new GwtEvent.Type<>();
        }
        return TYPE;
    }

    @Override
    public GwtEvent.Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onDoubleClick(this);
    }

    public interface Handler extends EventHandler {

        void onDoubleClick(DoubleClickEvent event);
    }
}
