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

package stroom.floormap.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A floor map's Editor tab (all its parts shown here by its controller):
///
/// ```
/// ┌─────────────────────────────────────────┬────────────┐
/// │            Map Canvas                   │    Dock    │  canvas fills,
/// │                                         │            │  dock east
/// ├─────────────────────────────────────────┴────────────┤  ◄─ draggable
/// │              Timeline                                │  fixed height
/// ├───────────────────────────┬──────────────────────────┤
/// │      Fact List            │        Time List         │  ~1/3 total height
/// └───────────────────────────┴──────────────────────────┘
///          ~50% width         ▲         ~50% width
///                             └── draggable
/// ```
public class FloorMapEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane topSplitPane;
    @UiField
    SimplePanel canvas;
    @UiField
    SimplePanel dock;
    @UiField
    SimplePanel timeline;
    @UiField
    SimplePanel factList;
    @UiField
    SimplePanel timeList;

    /// Makes the tab, with nothing in it.
    public FloorMapEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param canvas The map canvas.
    public void setCanvas(final IsWidget canvas) {
        this.canvas.setWidget(canvas);
    }

    /// @param dock The right-hand dock.
    public void setDock(final IsWidget dock) {
        this.dock.setWidget(dock);
    }

    /// @param timeline The timeline.
    public void setTimeline(final IsWidget timeline) {
        this.timeline.setWidget(timeline);
    }

    /// @param factList The Fact List.
    public void setFactList(final IsWidget factList) {
        this.factList.setWidget(factList);
    }

    /// @param timeList The Time List.
    public void setTimeList(final IsWidget timeList) {
        this.timeList.setWidget(timeList);
    }

    /// Shows or hides the dock and its splitter, keeping the width it was dragged to.
    ///
    /// @param visible Whether the dock shows.
    public void setDockVisible(final boolean visible) {
        topSplitPane.setPaneHidden(dock, !visible);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapEditor> {

    }
}
