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

package stroom.floormap.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/// Static help content for the Floor Map **Editor** tab.
///
/// Each method returns the HTML body shown in the in-app help popup fired by a
/// [stroom.widget.help.client.HelpButton] placed on the corresponding panel.
/// The strings are developer-authored constants (no user input), so they are wrapped
/// with [SafeHtmlUtils#fromTrustedString(String)] — the same mechanism the
/// Settings tab uses for its `<form:HelpHTML>` content, and rendered with the
/// shared `markdown` popup styling.
///
/// The help is only wired up by the Editor tab, so it does not appear on the
/// read-only Map tab (which shares the canvas and timeline widgets).
///
/// @see stroom.floormap.client.presenter.FloorMapEditorPresenter
public final class FloorMapEditorHelp {

    private FloorMapEditorHelp() {
        // Utility class.
    }

    /// Help for the map canvas — the interaction model: selecting, moving,
    /// rotating, scaling, panning, zooming, and the modifier keys.
    ///
    /// @return the canvas help HTML body
    public static SafeHtml canvas() {
        return SafeHtmlUtils.fromTrustedString(
                "<p>The map is the main editing surface. It shows the map's "
                + "<strong>facts</strong> — the background image, gates, desks, cameras and so "
                + "on — arranged as they were at the time selected on the timeline. Facts are "
                + "drawn from their image, or as a coloured shape when they have no image.</p>"

                + "<h4>Selecting</h4>"
                + "<ul>"
                + "<li><strong>Click</strong> an object to select it.</li>"
                + "<li><strong>Shift</strong>, <strong>Ctrl</strong> or <strong>Cmd</strong> + "
                + "click an object to add it to, or remove it from, the selection "
                + "(multi&#8209;select).</li>"
                + "<li>Hold <strong>Shift</strong>, <strong>Ctrl</strong> or <strong>Cmd</strong> "
                + "and <strong>drag across empty space</strong> to draw a rubber&#8209;band box; "
                + "every object it touches is <em>added</em> to the selection.</li>"
                + "<li>Click an empty area with no object (or press <strong>Esc</strong>) to "
                + "deselect everything.</li>"
                + "<li><strong>Hover</strong> an object — without clicking — for a panel giving "
                + "its name, type and position (and, on the Map tab, the areas it is inside). "
                + "Hovering a merged group of entities lists the entities in it instead.</li>"
                + "</ul>"

                + "<h4>Moving, rotating &amp; scaling</h4>"
                + "<p>A dashed frame with handles appears around the selection.</p>"
                + "<ul>"
                + "<li><strong>Move</strong> — drag any selected object. Dragging one member of a "
                + "multi&#8209;selection moves the whole group together.</li>"
                + "<li><strong>Scale</strong> — drag one of the four <em>square corner</em> "
                + "handles. Scaling keeps the aspect ratio and grows or shrinks the selection "
                + "about the opposite corner.</li>"
                + "<li><strong>Rotate</strong> — drag the <em>round</em> handle above the top of "
                + "the frame. The selection rotates about its centre; hold <strong>Shift</strong> "
                + "to snap to 15&deg; steps.</li>"
                + "</ul>"
                + "<p>While you move or resize, a label follows the cursor with the live "
                + "measurement: the selection's <strong>size</strong> as you scale it, and its "
                + "<strong>position</strong> as you move it.</p>"

                + "<h4>Panning &amp; zooming</h4>"
                + "<ul>"
                + "<li><strong>Pan</strong> — drag empty space to scroll the view.</li>"
                + "<li><strong>Zoom</strong> — turn the <strong>mouse wheel</strong> to zoom in "
                + "and out towards the pointer.</li>"
                + "</ul>"

                + "<h4>Right&#8209;click menu</h4>"
                + "<ul>"
                + "<li><strong>On empty space</strong> — <em>Add Object Here</em>.</li>"
                + "<li><strong>On an object</strong> — <em>Edit Properties</em>, <em>Add Time "
                + "Version</em>, <em>Duplicate</em> and <em>Delete</em>. (There is no Delete "
                + "key — delete objects from this menu.)</li>"
                + "<li><strong>On a multi&#8209;selection</strong> — <em>Duplicate</em> or "
                + "<em>Delete</em> everything selected.</li>"
                + "<li><strong>Anywhere</strong> — <em>Draw Area Here</em> and "
                + "<em>Set Scale</em>.</li>"
                + "</ul>"

                + "<h4>Scale</h4>"
                + "<p>Every size on the map — the grid labels, the scale bar in the corner, and "
                + "the readout that follows the cursor while you drag — is shown as a real "
                + "distance in metric, choosing millimetres, centimetres, metres or kilometres to "
                + "suit the value.</p>"
                + "<p>A new map starts at a nominal scale, so those distances mean nothing until "
                + "you set one. To do that, right&#8209;click and choose <strong>Set Scale</strong>, "
                + "then drag a line across something whose real length you know (a doorway, a "
                + "parking bay, a wall) and type that length in. Everything showing a size "
                + "relabels at once.</p>"

                + "<h4>Modifier keys</h4>"
                + "<ul>"
                + "<li><strong>Shift / Ctrl / Cmd</strong> — multi&#8209;select and "
                + "rubber&#8209;band select.</li>"
                + "<li><strong>Shift</strong> (while rotating) — snap rotation to 15&deg; "
                + "steps.</li>"
                + "<li><strong>Esc</strong> — clear the selection.</li>"
                + "</ul>"

                + "<p><em>The background image is itself a fact: click it to select it (then it "
                + "can be moved, scaled and rotated like anything else); while it is not selected, "
                + "dragging over it pans the view.</em></p>"

                + "<p>Each of the other panels — <em>Fact List</em>, <em>Time List</em> and "
                + "<em>Timeline</em> — has its own help button.</p>");
    }

    /// Help for the Fact List panel.
    ///
    /// @return the Fact List help HTML body
    public static SafeHtml factList() {
        return SafeHtmlUtils.fromTrustedString(
                "<p>Lists every <strong>object (fact)</strong> in the map — one row per object, "
                + "showing its <em>Key</em>, <em>Type</em> and <em>Name</em>.</p>"
                + "<p>Select a row to select that object on the map; hold <strong>Ctrl</strong> "
                + "or <strong>Shift</strong> and click to select several. The map highlights the "
                + "selection; when a <em>single</em> object is selected the <em>Time List</em> "
                + "and <em>Properties</em> update to match it (selecting several clears "
                + "them).</p>"
                + "<h4>Toolbar</h4>"
                + "<ul>"
                + "<li><strong>Add</strong> — create a new object at the centre of the current "
                + "view and open its properties.</li>"
                + "<li><strong>Delete</strong> — delete the selected object and all of its time "
                + "versions. Enabled only when an object is selected. With several selected it "
                + "removes only the primary one — use the map's right&#8209;click menu to delete "
                + "a whole group.</li>"
                + "<li><strong>Show all</strong> (clock) — a toggle. When <em>on</em>, every "
                + "object in the store is listed regardless of the timeline position; when "
                + "<em>off</em>, only objects present at the current time are listed.</li>"
                + "</ul>");
    }

    /// Help for the Time List panel.
    ///
    /// @return the Time List help HTML body
    public static SafeHtml timeList() {
        return SafeHtmlUtils.fromTrustedString(
                "<p>Shows the <strong>time versions</strong> of the object selected in the Fact "
                + "List. Each row is the object's state from a given <em>effective time</em>, so "
                + "an object can look different at different points on the timeline.</p>"
                + "<p>Select an object in the Fact List first. Clicking a row here moves the "
                + "timeline to that version's time.</p>"
                + "<h4>Toolbar</h4>"
                + "<ul>"
                + "<li><strong>Edit</strong> — edit the selected version's properties. Enabled "
                + "only when a version is selected.</li>"
                + "<li><strong>Add</strong> — add a new version at the current timeline time, "
                + "cloned from the version in effect then.</li>"
                + "<li><strong>Delete</strong> — delete the selected version. Enabled only when a "
                + "version is selected.</li>"
                + "</ul>");
    }

    /// Help for the Timeline control.
    ///
    /// @return the timeline help HTML body
    public static SafeHtml timeline() {
        return SafeHtmlUtils.fromTrustedString(
                "<p>Chooses the point in time the map shows. Facts and moving events are drawn as "
                + "they were at the selected time, and playback animates through time like a "
                + "video.</p>"
                + "<h4>Controls</h4>"
                + "<ul>"
                + "<li><strong>Step back / forward</strong> — jump one step (one histogram bin) "
                + "earlier or later.</li>"
                + "<li><strong>Play / Pause</strong> — animate time forward at the chosen speed; "
                + "click again to pause.</li>"
                + "<li><strong>Scrubber</strong> — drag the handle, or click the histogram, to "
                + "jump to a time. A pill above the handle shows the exact time while "
                + "dragging.</li>"
                + "<li><strong>Histogram</strong> — the bars show how many events fall in each "
                + "time bin; hover a bar for its count.</li>"
                + "<li><strong>Speed badge</strong> (e.g. <code>x1</code>) — the current playback "
                + "speed; click it to pick a different speed.</li>"
                + "<li><strong>Settings</strong> (gear) — loop on/off, the visible "
                + "start/end date range, and <em>Show All</em> (widen the range to cover all the "
                + "data).</li>"
                + "<li><strong>&#171; / &#187;</strong> — warn that a selected object's time is "
                + "before or after the visible range; widen the range to see it.</li>"
                + "</ul>");
    }
}
