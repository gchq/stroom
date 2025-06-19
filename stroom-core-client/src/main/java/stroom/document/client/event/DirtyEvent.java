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

package stroom.document.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class DirtyEvent extends GwtEvent<DirtyEvent.DirtyHandler> {

    private static Type<DirtyHandler> TYPE;
    private final boolean dirty;

    private DirtyEvent(final boolean dirty) {
        this.dirty = dirty;
    }

    public static void fire(final HasHandlers handlers, final boolean dirty) {
        handlers.fireEvent(new DirtyEvent(dirty));
    }

    public static Type<DirtyHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<DirtyHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final DirtyHandler handler) {
        handler.onDirty(this);
    }

    public boolean isDirty() {
        return dirty;
    }

    public interface DirtyHandler extends EventHandler {

        void onDirty(DirtyEvent event);
    }
}
