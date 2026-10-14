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

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

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
/// Uses three nested [ThinSplitLayoutPanel]s plus a [DockLayoutPanel]:
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

    /// Initial height of the bottom strip in pixels.
    private static final int BOTTOM_STRIP_INITIAL_HEIGHT = 250;

    /// Proportional height of the bottom strip — 1/3 of total, leaving 2/3 for the canvas and
    /// right-hand dock above. The timeline is inside this strip, not above it.
    private static final double BOTTOM_STRIP_SPLIT = 1.0 / 3.0;

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

    /// Initial width of the West-anchored Fact List column; the Time List fills the rest.
    private static final int BOTTOM_COLUMN_INITIAL_WIDTH = 300;

    // -----------------------------------------------------------------------
    // Top-inner (horizontal) split — canvas beside the right-hand dock
    // -----------------------------------------------------------------------

    /// Initial width of the right-hand dock in pixels.
    private static final int DOCK_INITIAL_WIDTH = 200;

    // -----------------------------------------------------------------------

    private final ThinSplitLayoutPanel outerSplitPanel;
    private final ThinSplitLayoutPanel topSplitPanel;

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

        topSplitPanel = new ThinSplitLayoutPanel();
        topSplitPanel.setSize("100%", "100%");
        topSplitPanel.addEast(dockPanel, DOCK_INITIAL_WIDTH);   // right-hand dock
        topSplitPanel.add(canvasPanel);                          // canvas (fills rest)
        // Start visible to match the toggle button's initial on state
        // (FloorMapEditorPresenter sets dockToggleButton state = true).
        topSplitPanel.setWidgetHidden(dockPanel, false);

        timelinePanel = new SimplePanel();
        timelinePanel.addStyleName("dashboard-panel overflow-hidden stroom-border-bottom");

        // ---- Bottom strip: two horizontal columns (Fact List | Time List) --
        factListPanel = new SimplePanel();
        factListPanel.addStyleName("dashboard-panel overflow-hidden");

        timeListPanel = new SimplePanel();
        timeListPanel.addStyleName("dashboard-panel overflow-hidden");

        // Bottom strip: two equal columns
        final ThinSplitLayoutPanel bottomSplitPanel = new ThinSplitLayoutPanel();
        bottomSplitPanel.setSize("100%", "100%");
        bottomSplitPanel.setHSplits(0.5);
        bottomSplitPanel.addWest(factListPanel, BOTTOM_COLUMN_INITIAL_WIDTH);   // Fact List
        bottomSplitPanel.add(timeListPanel);                                     // Time List (fills rest)

        // Combine timeline and bottom
        final DockLayoutPanel bottomPanel = new DockLayoutPanel(Unit.PX);
        bottomPanel.addNorth(timelinePanel, TIMELINE_HEIGHT);
        bottomPanel.add(bottomSplitPanel);

        // ---- Outer vertical split: top area vs bottom strip -----------------
        outerSplitPanel = new ThinSplitLayoutPanel();
        outerSplitPanel.setSize("100%", "100%");
        // Thin divider between the document toolbar and the canvas area.
        outerSplitPanel.addStyleName("stroom-border-top");
        outerSplitPanel.setVSplits(BOTTOM_STRIP_SPLIT);
        outerSplitPanel.addSouth(bottomPanel, BOTTOM_STRIP_INITIAL_HEIGHT); // bottom strip
        outerSplitPanel.add(topSplitPanel);                                     // canvas + dock (centre)
    }

    @Override
    public Widget asWidget() {
        return outerSplitPanel;
    }

    /// Routes GWTP slot content into the correct panel — all five slots:
    ///
    /// - [FloorMapEditorPresenter#MAIN]       → canvas panel (centre of the top area)
    /// - [FloorMapEditorPresenter#DOCK]       → right-hand dock, beside the canvas
    /// - [FloorMapEditorPresenter#TIMELINE]   → timeline strip (north of the bottom
    ///   strip, fixed height)
    /// - [FloorMapEditorPresenter#FACT_LIST]  → bottom-left column
    /// - [FloorMapEditorPresenter#TIME_LIST]  → bottom-right column (fills remaining
    ///   space)
    ///
    /// Properties are shown as a modal dialog and have no slot.
    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (FloorMapEditorPresenter.MAIN.equals(slot)) {
            canvasPanel.setWidget(content);
        } else if (FloorMapEditorPresenter.DOCK.equals(slot)) {
            dockPanel.setWidget(content);
        } else if (FloorMapEditorPresenter.TIMELINE.equals(slot)) {
            timelinePanel.setWidget(content);
        } else if (FloorMapEditorPresenter.FACT_LIST.equals(slot)) {
            factListPanel.setWidget(content);
        } else if (FloorMapEditorPresenter.TIME_LIST.equals(slot)) {
            timeListPanel.setWidget(content);
        }
    }

    @Override
    public void setDockVisible(final boolean visible) {
        // Hides the dock and its splitter while retaining the dragged width.
        topSplitPanel.setWidgetHidden(dockPanel, !visible);
    }
}
