/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.event;

import stroom.floormap.client.model.FloorMapObject;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

/**
 * Event fired when new queried map objects are received from the query model.
 */
public class FloorMapDataEvent extends GwtEvent<FloorMapDataEvent.Handler> {

    private static Type<Handler> TYPE;
    private final String docUuid;
    private final List<FloorMapObject> objects;

    public FloorMapDataEvent(final String docUuid, final List<FloorMapObject> objects) {
        this.docUuid = docUuid;
        this.objects = objects;
    }

    public static void fire(final HasHandlers handlers,
                            final String docUuid,
                            final List<FloorMapObject> objects) {
        handlers.fireEvent(new FloorMapDataEvent(docUuid, objects));
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
        handler.onDataChange(this);
    }

    /**
     * @return the UUID of the document whose query produced these objects, so
     *         receivers on the shared event bus can ignore events from other
     *         open FloorMap documents; may be {@code null}
     */
    public String getDocUuid() {
        return docUuid;
    }

    /**
     * @return the list of map objects received from the query model
     */
    public List<FloorMapObject> getObjects() {
        return objects;
    }

    // --------------------------------------------------------------------------------

    /**
     * Handler for {@link FloorMapDataEvent}.
     */
    public interface Handler extends EventHandler {

        void onDataChange(FloorMapDataEvent event);
    }

}
