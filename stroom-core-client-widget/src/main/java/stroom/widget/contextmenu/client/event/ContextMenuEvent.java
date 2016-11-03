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

package stroom.widget.contextmenu.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class ContextMenuEvent extends GwtEvent<ContextMenuEvent.Handler> {
    public interface Handler extends EventHandler {
        void onContextMenu(ContextMenuEvent event);
    }

    private static Type<Handler> TYPE;

    private final int x;
    private final int y;

    protected ContextMenuEvent(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public static <I> void fire(final HasContextMenuHandlers source, final int x, final int y) {
        if (TYPE != null) {
            source.fireEvent(new ContextMenuEvent(x, y));
        }
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onContextMenu(this);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
