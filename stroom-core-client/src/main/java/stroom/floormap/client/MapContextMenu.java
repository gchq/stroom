/*
 * Copyright 2026 Crown Copyright
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

package stroom.floormap.client;



/// What the user right-clicked on the floor map canvas, which the canvas gives its owner
/// (`FloorMapCanvasController.setOnContextMenu`).
///
/// Carries the ID of the map object under the cursor (or `null` when the
/// click landed on empty canvas), the logical map-space coordinates of the
/// click, and the screen (client) coordinates suitable for positioning a popup
/// context menu.
public final class MapContextMenu {

    private final String objectId;
    private final double mapX;
    private final double mapY;
    private final int clientX;
    private final int clientY;
    private final int vertexIndex;

    /// Creates a new `MapContextMenu` for a non-vertex right-click.
    ///
    /// @param objectId the ID of the right-clicked map object, or `null`
    ///         if the click was on empty canvas
    /// @param mapX     the X coordinate in map space
    /// @param mapY     the Y coordinate in map space
    /// @param clientX  the screen X coordinate for popup positioning
    /// @param clientY  the screen Y coordinate for popup positioning
    public MapContextMenu(final String objectId,
                               final double mapX,
                               final double mapY,
                               final int clientX,
                               final int clientY) {
        this(objectId, mapX, mapY, clientX, clientY, -1);
    }

    /// Creates a new `MapContextMenu`.
    ///
    /// @param objectId    the ID of the right-clicked map object, or `null`
    ///         if the click was on empty canvas
    /// @param mapX        the X coordinate in map space
    /// @param mapY        the Y coordinate in map space
    /// @param clientX     the screen X coordinate for popup positioning
    /// @param clientY     the screen Y coordinate for popup positioning
    /// @param vertexIndex the index of the right-clicked area vertex handle, or
    ///         `-1` when the click was not on a vertex handle
    public MapContextMenu(final String objectId,
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

}
