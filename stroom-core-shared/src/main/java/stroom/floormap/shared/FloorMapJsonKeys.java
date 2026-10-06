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

package stroom.floormap.shared;

/// JSON field-name constants for the temporal-store entry value schema
/// used across the floor map feature.
public final class FloorMapJsonKeys {

    public static final String TYPE = "type";
    public static final String NAME = "name";

    /// Object ID and type identifier for the background layer/object
    /// in the floor map canvas.
    public static final String BACKGROUND = "background";

    /// Type identifier for person objects in the floor map canvas.
    /// Objects with this type receive animated movement and trail rendering
    /// during timeline playback.
    public static final String PERSON = "person";

    /// Type identifier for user-drawn area polygons in the floor map canvas.
    /// Area facts carry a `geometry` vertex array and render as filled
    /// polygons beneath other objects.
    public static final String AREA = "area";

    /// JSON field name for an area's polygon vertices — a flat array of
    /// alternating x and y values in the fact's local frame.
    public static final String GEOMETRY = "geometry";

    /// JSON field name for an area's fill opacity (number in `[0, 1]`).
    public static final String OPACITY = "opacity";

    /// Display name for the background object in the fact list UI.
    public static final String BACKGROUND_DISPLAY_NAME = "Background";

    /// ID prefix applied to SVG `<g>` wrapper elements in the canvas.
    ///
    /// Each map object is rendered inside a `<g>` whose ID is
    /// `SVG_GROUP_PREFIX + objectKey`. The click-detection logic uses
    /// this prefix to distinguish wrapper groups (ignored) from the actual
    /// clickable shape elements (whose IDs are the raw object keys).
    ///
    /// The prefix uses a double-underscore convention (`"__g_"`) to
    /// avoid collisions with user-chosen object keys — users are unlikely to
    /// name objects starting with `"__"`.
    public static final String SVG_GROUP_PREFIX = "__g_";

    /// ID prefix applied to the selection transform handles (scale/rotate) drawn
    /// over the current selection in edit mode. The mousedown hit-test checks
    /// this prefix *first* so a handle drag starts a scale/rotate gesture
    /// rather than being treated as a click on an object literally named
    /// `"__handle_..."`. The id format is `HANDLE_PREFIX + role`
    /// (e.g. `"__handle_scale-nw"`, `"__handle_rotate"`).
    public static final String HANDLE_PREFIX = "__handle_";

    /// ID prefix applied to a cluster's summary glyph — the single glyph drawn in
    /// place of entities too close together on screen to be told apart (see
    /// `FloorMapClusterOverlay`). The id format is
    /// `CLUSTER_PREFIX + FloorMapCluster#getKey()`.
    ///
    /// A cluster is **not** an entity: it has no row in the
    /// tracking roster and no fact behind it, so the mousedown hit-test must not
    /// report it as an object. It carries its own prefix for exactly that reason —
    /// the object hit-test skips it, and a separate resolver recognises it.
    public static final String CLUSTER_PREFIX = "__cluster_";

    private FloorMapJsonKeys() {
        // Utility class
    }
}
