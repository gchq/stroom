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

package stroom.floormap.client.view;

import stroom.floormap.client.presenter.FloorMapEditorPresenter;
import stroom.floormap.client.presenter.FloorMapEditorPresenter.FloorMapEditorView;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/// View implementation for the FloorMap Editor tab.
///
/// ### Layout
///
/// ```
/// ┌─────────────────────────────────────────┬────────────┐
/// │            Map Canvas  (MAIN)           │    DOCK    │  canvas fills,
/// │                                         │            │  dock east
/// ├─────────────────────────────────────────┴────────────┤  ◄─ draggable
/// │              Timeline control (TIMELINE)             │  fixed, see
/// ├───────────────────────────┬──────────────────────────┤  TIMELINE_HEIGHT
/// │      Fact List            │        Time List         │  ~1/3 total height
/// │     (FACT_LIST)           │       (TIME_LIST)        │
/// └───────────────────────────┴──────────────────────────┘
///          ~50% width         ▲         ~50% width
///                             └── draggable
/// ```
///
/// Uses three nested [SplitPane]s plus a [DockLayoutPanel]:
///
/// - **Outer (vertical)** — top area vs bottom strip (draggable).
/// - **Top-inner (horizontal)** — canvas (fill) beside the right-hand dock
///   (fixed east, hideable via the toolbar toggle).
/// - **Bottom-inner (horizontal)** — two equal columns, Fact List | Time List
///   (draggable).
/// - **`DockLayoutPanel`** — combines the timeline (fixed north) with the
///   bottom-inner columns; this is why the timeline is not a child of the top
///   area even though it is drawn directly beneath the canvas.
public class FloorMapEditorViewImpl extends ViewImpl implements FloorMapEditorView {

    // -----------------------------------------------------------------------
    // Outer (vertical) split — top area vs bottom strip
    // -----------------------------------------------------------------------

    /// Proportional height of the bottom strip — 1/3 of total, leaving 2/3 for the canvas and
    /// right-hand dock above. The timeline is inside this strip, not above it.
    private static final String BOTTOM_STRIP_SIZE = "33%";

    // -----------------------------------------------------------------------
    // Top-inner (vertical) split — canvas above timeline
    // -----------------------------------------------------------------------

    /// Fixed height of the timeline strip in pixels. The timeline is a compact
    /// bar (date pickers, scrubber, play button, speed selector) and does not
    /// need to be user-resizable — it is docked to the north of the bottom strip, above the Fact
    /// and Time lists, with no split ratio, so it keeps this fixed height when the window is
    /// resized.
    private static final int TIMELINE_HEIGHT = 110;

    // -----------------------------------------------------------------------
    // Bottom-inner (horizontal) split — Fact List beside Time List
    // -----------------------------------------------------------------------

    /// Proportional width of the Fact List column; the Time List fills the rest.
    private static final String FACT_LIST_SIZE = "50%";

    // -----------------------------------------------------------------------
    // Top-inner (horizontal) split — canvas beside the right-hand dock
    // -----------------------------------------------------------------------

    /// Initial width of the right-hand dock.
    private static final String DOCK_INITIAL_WIDTH = "200px";

    // -----------------------------------------------------------------------

    private final SplitPane outerSplitPane;
    private final SplitPane topSplitPane;

    private final SimplePanel canvasPanel;
    private final SimplePanel dockPanel;
    private final SimplePanel timelinePanel;

    private final SimplePanel factListPanel;
    private final SimplePanel timeListPanel;

    @Inject
    public FloorMapEditorViewImpl() {

        // ---- Top area: canvas (fill) beside the right-hand dock (east) ------
        canvasPanel = new SimplePanel();
        canvasPanel.addStyleName("dashboard-panel overflow-hidden");

        dockPanel = new SimplePanel();
        dockPanel.addStyleName("dashboard-panel overflow-hidden");

        topSplitPane = new SplitPane();
        topSplitPane.setThin(true);
        topSplitPane.setSizes("*, " + DOCK_INITIAL_WIDTH);
        topSplitPane.setNames("Map, Controls");
        topSplitPane.add(canvasPanel);  // canvas (fills rest)
        topSplitPane.add(dockPanel);    // right-hand dock
        // Starts visible to match the toggle button's initial on state
        // (FloorMapEditorPresenter presses dockToggleButton).

        timelinePanel = new SimplePanel();
        timelinePanel.addStyleName("dashboard-panel overflow-hidden stroom-border-bottom");

        // ---- Bottom strip: two horizontal columns (Fact List | Time List) --
        factListPanel = new SimplePanel();
        factListPanel.addStyleName("dashboard-panel overflow-hidden");

        timeListPanel = new SimplePanel();
        timeListPanel.addStyleName("dashboard-panel overflow-hidden");

        // Bottom strip: two equal columns
        final SplitPane bottomSplitPane = new SplitPane();
        bottomSplitPane.setThin(true);
        bottomSplitPane.setSizes(FACT_LIST_SIZE + ", *");
        bottomSplitPane.setNames("Fact List, Time List");
        bottomSplitPane.add(factListPanel);  // Fact List
        bottomSplitPane.add(timeListPanel);  // Time List (fills rest)

        // Combine timeline and bottom
        final DockLayoutPanel bottomPanel = new DockLayoutPanel(Unit.PX);
        bottomPanel.addNorth(timelinePanel, TIMELINE_HEIGHT);
        bottomPanel.add(bottomSplitPane);

        // ---- Outer vertical split: top area vs bottom strip -----------------
        outerSplitPane = new SplitPane();
        outerSplitPane.setVertical(true);
        outerSplitPane.setThin(true);
        outerSplitPane.setSize("100%", "100%");
        // Thin divider between the document toolbar and the canvas area.
        outerSplitPane.addStyleName("stroom-border-top");
        outerSplitPane.setSizes("*, " + BOTTOM_STRIP_SIZE);
        outerSplitPane.setNames("Map and Controls, Timeline and Lists");
        outerSplitPane.add(topSplitPane);  // canvas + dock (fills rest)
        outerSplitPane.add(bottomPanel);   // bottom strip
    }

    @Override
    public Widget asWidget() {
        return outerSplitPane;
    }

    @Override
    public void setCanvasView(final IsWidget view) {
        canvasPanel.setWidget(view.asWidget());
    }

    @Override
    public void setDockView(final IsWidget view) {
        dockPanel.setWidget(view.asWidget());
    }

    @Override
    public void setTimelineView(final IsWidget timeline) {
        timelinePanel.setWidget(timeline.asWidget());
    }

    @Override
    public void setFactListView(final IsWidget view) {
        factListPanel.setWidget(view.asWidget());
    }

    @Override
    public void setTimeListView(final IsWidget view) {
        timeListPanel.setWidget(view.asWidget());
    }

    @Override
    public void setDockVisible(final boolean visible) {
        // Hides the dock and its splitter while retaining the dragged width.
        topSplitPane.setPaneHidden(dockPanel, !visible);
    }
}
