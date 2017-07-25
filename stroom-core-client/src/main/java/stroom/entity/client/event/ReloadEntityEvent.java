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

package stroom.entity.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.entity.shared.Entity;

public class ReloadEntityEvent extends GwtEvent<ReloadEntityEvent.Handler> {
    private static Type<Handler> TYPE;
    private final Entity entity;

    private ReloadEntityEvent(final Entity entity) {
        this.entity = entity;
    }

    public static void fire(final HasHandlers handlers, final Entity entity) {
        handlers.fireEvent(new ReloadEntityEvent(entity));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onReload(this);
    }

    public Entity getEntity() {
        return entity;
    }

    public interface Handler extends EventHandler {
        void onReload(ReloadEntityEvent event);
    }
}
