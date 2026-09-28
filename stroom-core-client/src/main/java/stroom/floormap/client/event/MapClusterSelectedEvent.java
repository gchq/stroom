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

import stroom.floormap.client.cluster.FloorMapCluster;
import stroom.floormap.client.event.MapClusterSelectedEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/**
 * Event fired when a cluster's summary glyph is clicked on the floor map canvas.
 *
 * <p>Deliberately <strong>not</strong> a {@link MapObjectSelectedEvent}: a cluster
 * is not an entity. It has no row in the tracking roster and no fact behind it, so
 * announcing it as a selected object would name an entity the user cannot see and
 * did not aim at. The owning tab responds by listing the members instead, which is
 * what makes them reachable.</p>
 *
 * <p>Carries the whole cluster rather than its key, because the cluster is a
 * per-frame value: by the time a handler ran, the next frame may have dissolved
 * it, and the list the user asked for is the one they clicked on.</p>
 */
public class MapClusterSelectedEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;
    private final FloorMapCluster cluster;

    public MapClusterSelectedEvent(final FloorMapCluster cluster) {
        this.cluster = cluster;
    }

    public static void fire(final HasHandlers handlers, final FloorMapCluster cluster) {
        handlers.fireEvent(new MapClusterSelectedEvent(cluster));
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
    public void dispatch(final Handler handler) {
        handler.onSelect(this);
    }

    /** The clicked cluster, as it was drawn on the frame that was clicked. */
    public FloorMapCluster getCluster() {
        return cluster;
    }

    // --------------------------------------------------------------------------------

    /**
     * Handler for {@link MapClusterSelectedEvent}.
     */
    public interface Handler extends EventHandler {

        void onSelect(MapClusterSelectedEvent event);
    }
}
