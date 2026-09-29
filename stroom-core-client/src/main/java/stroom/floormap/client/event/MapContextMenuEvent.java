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

import stroom.floormap.client.event.MapContextMenuEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/// Event fired when the user right-clicks on the floor map canvas.
///
/// Carries the ID of the map object under the cursor (or `null` when the
/// click landed on empty canvas), the logical map-space coordinates of the
/// click, and the screen (client) coordinates suitable for positioning a popup
/// context menu.
public class MapContextMenuEvent extends GwtEvent<Handler> {
    private static Type<Handler> TYPE;
    private final String objectId;
    private final double mapX;
    private final double mapY;
    private final int clientX;
    private final int clientY;
    private final int vertexIndex;

    /// Creates a new `MapContextMenuEvent` for a non-vertex right-click.
    ///
    /// @param objectId the ID of the right-clicked map object, or `null`
    ///         if the click was on empty canvas
    /// @param mapX     the X coordinate in map space
    /// @param mapY     the Y coordinate in map space
    /// @param clientX  the screen X coordinate for popup positioning
    /// @param clientY  the screen Y coordinate for popup positioning
    public MapContextMenuEvent(final String objectId,
                               final double mapX,
                               final double mapY,
                               final int clientX,
                               final int clientY) {
        this(objectId, mapX, mapY, clientX, clientY, -1);
    }

    /// Creates a new `MapContextMenuEvent`.
    ///
    /// @param objectId    the ID of the right-clicked map object, or `null`
    ///         if the click was on empty canvas
    /// @param mapX        the X coordinate in map space
    /// @param mapY        the Y coordinate in map space
    /// @param clientX     the screen X coordinate for popup positioning
    /// @param clientY     the screen Y coordinate for popup positioning
    /// @param vertexIndex the index of the right-clicked area vertex handle, or
    ///         `-1` when the click was not on a vertex handle
    public MapContextMenuEvent(final String objectId,
                               final double mapX,
                               final double mapY,
                               final int clientX,
                               final int clientY,
                               final int vertexIndex) {
        this.objectId = objectId;
        this.mapX = mapX;
        this.mapY = mapY;
        this.clientX = clientX;
        this.clientY = clientY;
        this.vertexIndex = vertexIndex;
    }

    /// Fires a `MapContextMenuEvent` on the given handler source.
    ///
    /// @param handlers the source capable of firing events
    /// @param objectId the ID of the right-clicked map object, or `null`
    /// @param mapX     the X coordinate in map space
    /// @param mapY     the Y coordinate in map space
    /// @param clientX  the screen X coordinate for popup positioning
    /// @param clientY  the screen Y coordinate for popup positioning
    public static void fire(final HasHandlers handlers,
                            final String objectId,
                            final double mapX,
                            final double mapY,
                            final int clientX,
                            final int clientY) {
        handlers.fireEvent(new MapContextMenuEvent(objectId, mapX, mapY, clientX, clientY));
    }

    /// Fires a `MapContextMenuEvent` for a right-click on an area vertex
    /// handle.
    ///
    /// @param handlers    the source capable of firing events
    /// @param objectId    the area fact's key
    /// @param mapX        the X coordinate in map space
    /// @param mapY        the Y coordinate in map space
    /// @param clientX     the screen X coordinate for popup positioning
    /// @param clientY     the screen Y coordinate for popup positioning
    /// @param vertexIndex the index of the right-clicked vertex handle
    public static void fireVertex(final HasHandlers handlers,
                                  final String objectId,
                                  final double mapX,
                                  final double mapY,
                                  final int clientX,
                                  final int clientY,
                                  final int vertexIndex) {
        handlers.fireEvent(new MapContextMenuEvent(
                objectId, mapX, mapY, clientX, clientY, vertexIndex));
    }

    /// Returns the singleton event type, creating it on first access.
    ///
    /// @return the [Type] for `MapContextMenuEvent`
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
        handler.onContextMenu(this);
    }

    /// Returns the ID of the map object that was right-clicked.
    ///
    /// @return the object ID, or `null` if the click was on empty canvas
    public String getObjectId() {
        return objectId;
    }

    /// Returns the X coordinate of the right-click in map space.
    ///
    /// @return the map-space X coordinate
    public double getMapX() {
        return mapX;
    }

    /// Returns the Y coordinate of the right-click in map space.
    ///
    /// @return the map-space Y coordinate
    public double getMapY() {
        return mapY;
    }

    /// Returns the screen X coordinate of the right-click, suitable for
    /// positioning a popup menu.
    ///
    /// @return the client X coordinate
    public int getClientX() {
        return clientX;
    }

    /// Returns the screen Y coordinate of the right-click, suitable for
    /// positioning a popup menu.
    ///
    /// @return the client Y coordinate
    public int getClientY() {
        return clientY;
    }

    /// Returns the index of the right-clicked area vertex handle.
    ///
    /// @return the vertex index, or `-1` if the click was not on a vertex
    ///         handle
    public int getVertexIndex() {
        return vertexIndex;
    }

    // --------------------------------------------------------------------------------

    /// Handler interface for [MapContextMenuEvent].
    public interface Handler extends EventHandler {

        /// Called when a context menu event is fired on the floor map canvas.
        ///
        /// @param event the context menu event
        void onContextMenu(MapContextMenuEvent event);
    }
}
