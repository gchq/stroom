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

package stroom.dashboard.client.vis;

import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import java.util.HashMap;
import java.util.Map;

/// The controls a visualisation has for the toolbar Stroom shows above it ([VisToolbar]), as it sends them with
/// `stroom.controls` (see `vis.js`): whether it zooms (and is zoomed), has a range tool (and it is on), and selects
/// what is clicked (and what is selected), with a short note. A button in the toolbar sends the visualisation a
/// command ([#RESET_ZOOM], [#RANGE_MODE] or [#CLEAR_SELECTION]).
///
/// Reading them ([#of(Map)]) works on plain Java values, so it can be tested on the JVM; [#of(JSONObject)] only
/// converts from the browser's JSON. Any script in the frame can send them, so only what is expected is kept, and
/// text is kept short and to one line.
///
/// @param zoom       It zooms: Reset zoom is shown.
/// @param zoomed     It is zoomed in: Reset zoom is enabled.
/// @param range      It has a range tool: Select a time range is shown.
/// @param rangeMode  Its range tool is on: dragging across it selects a time range.
/// @param selectable A click on it selects what was clicked: Clear the selection is shown.
/// @param selected   What is selected, as it describes it, or null if nothing is.
/// @param note       A short note shown in the toolbar (e.g. that rows were left out), or null.
public record VisControls(boolean zoom,
                          boolean zoomed,
                          boolean range,
                          boolean rangeMode,
                          boolean selectable,
                          String selected,
                          String note) {

    /// Puts the visualisation's zoom back.
    public static final String RESET_ZOOM = "resetZoom";
    /// Turns the visualisation's range tool on, or off.
    public static final String RANGE_MODE = "rangeMode";
    /// Clears what is selected on the visualisation.
    public static final String CLEAR_SELECTION = "clearSelection";
    /// No controls: the toolbar shows only the refresh button.
    public static final VisControls NONE = new VisControls(false, false, false, false, false, null, null);

    static final int MAX_TEXT = 200;

    /// @param controls The controls a visualisation sent, or null for none.
    /// @return The controls, only what is expected kept.
    public static VisControls of(final JSONObject controls) {
        if (controls == null) {
            return NONE;
        }
        final Map<String, Object> values = new HashMap<>();
        for (final String key : controls.keySet()) {
            final JSONValue value = controls.get(key);
            final JSONBoolean bool = value == null
                    ? null
                    : value.isBoolean();
            final JSONString string = value == null
                    ? null
                    : value.isString();
            if (bool != null) {
                values.put(key, bool.booleanValue());
            } else if (string != null) {
                values.put(key, string.stringValue());
            }
        }
        return of(values);
    }

    /// @param controls The controls a visualisation sent, as plain values, or null for none.
    /// @return The controls, only what is expected kept.
    public static VisControls of(final Map<String, Object> controls) {
        if (controls == null) {
            return NONE;
        }
        return new VisControls(
                Boolean.TRUE.equals(controls.get("zoom")),
                Boolean.TRUE.equals(controls.get("zoomed")),
                Boolean.TRUE.equals(controls.get("range")),
                Boolean.TRUE.equals(controls.get("rangeMode")),
                Boolean.TRUE.equals(controls.get("selectable")),
                text(controls.get("selected")),
                text(controls.get("note")));
    }

    // Text as shown in the toolbar: on one line, at most MAX_TEXT characters; null if blank or not text.
    static String text(final Object value) {
        if (!(value instanceof final String string)) {
            return null;
        }
        // Not \p{Cntrl}, which the browser's regular expressions don't have.
        final String line = string.replaceAll("[\\u0000-\\u001f\\u007f\\s]+", " ").trim();
        if (line.isEmpty()) {
            return null;
        }
        return line.length() > MAX_TEXT
                ? line.substring(0, MAX_TEXT - 1) + "…"
                : line;
    }
}
