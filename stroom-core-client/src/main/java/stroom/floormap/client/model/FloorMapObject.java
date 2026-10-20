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

package stroom.floormap.client.model;

import java.util.List;

/// Represents a single positioned entity on a floor map.
///
/// Each object has a unique identifier, a type (e.g. person, device), and mutable
/// `(x, y)` coordinates in the map's coordinate space. An optional movement
/// trail can be attached for client-side animation during temporal playback.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM.
public class FloorMapObject {
    private final String id;
    private final String type;

    private double x;
    private double y;

    /// Optional movement trail for animated objects during playback.
    /// Each entry is a `double[3]` of `{mapX, mapY, alpha}`, where
    /// `alpha` is in `[0.0, 1.0]` and represents how opaque that trail segment
    /// should be (1.0 = fully visible, 0.0 = invisible).
    ///
    /// This field is purely a client-side decoration and is never serialised to
    /// the server.  It is `null` for non-animated objects.
    private List<double[]> trail;

    /// Optional image-bearing fact twin for this entity: when the same key
    /// exists as a fact carrying an image (an asset attached to the entity),
    /// the canvas renders that image — scaled by the fact's world-to-map
    /// matrix — at this object's live position, instead of the generic type
    /// glyph.
    ///
    /// Purely a client-side decoration set by the canvas presenter; never
    /// serialised to the server. `null` when the entity has no
    /// image-bearing fact.
    private Fact imageFact;

    /// Optional key of the fact this entity's event named as its location — the
    /// desk, gate or camera the event happened at.
    ///
    /// Set when the events query's location column holds a reference rather than
    /// literal coordinates; [FloorMapLocationResolver] then places the
    /// entity at that fact's current position, so moving the fact moves the
    /// entity. `null` for an entity that carries its own coordinates.
    ///
    /// Client-side only; never serialised to the server.
    private String locationRef;

    public FloorMapObject(final String id,
                          final String type,
                          final double x,
                          final double y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(final double x) {
        this.x = x;
    }

    public void setY(final double y) {
        this.y = y;
    }

    /// Returns the movement trail for this object, or `null` if no trail is present.
    /// Each element is `{mapX, mapY, alpha}` where alpha ∈ `[0, 1]`.
    public List<double[]> getTrail() {
        return trail;
    }

    /// Attaches a movement trail to this object.  Only set by the client-side animation
    /// system; never read or written on the server side.
    ///
    /// @param trail List of `{mapX, mapY, alpha}` triples, oldest first.
    public void setTrail(final List<double[]> trail) {
        this.trail = trail;
    }

    /// Returns this entity's image-bearing fact twin, or `null` when the
    /// entity should render as the generic type glyph.
    public Fact getImageFact() {
        return imageFact;
    }

    /// Attaches (or clears, with `null`) the image-bearing fact twin.
    /// Only set by the client-side canvas presenter; never serialised.
    public void setImageFact(final Fact imageFact) {
        this.imageFact = imageFact;
    }

    /// Returns the key of the fact this entity is located at, or `null`
    /// when it carries its own coordinates.
    public String getLocationRef() {
        return locationRef;
    }

    /// Records the fact key this entity's event named as its location. Set by
    /// the events-query parser; read by [FloorMapLocationResolver].
    public void setLocationRef(final String locationRef) {
        this.locationRef = locationRef;
    }
}
