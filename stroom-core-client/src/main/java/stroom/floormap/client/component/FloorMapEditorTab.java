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

import stroom.entity.client.component.LinkTabPanel;
import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
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
public class FloorMapEditorTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane topSplitPane;
    @UiField
    FloorMapCanvas canvas;
    @UiField
    LinkTabPanel dock;
    @UiField
    FloorMapTimeline timeline;
    @UiField
    FloorMapFactList factList;
    @UiField
    FloorMapTimeList timeList;

    /// Makes the tab, with nothing in it.
    public FloorMapEditorTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The map canvas, which its controller attaches the canvas's controller to.
    public FloorMapCanvas getCanvas() {
        return canvas;
    }

    /// @return The right-hand dock, for its controller to attach to.
    public LinkTabPanel getDock() {
        return dock;
    }

    /// @return The timeline, which its controller attaches the timeline's controller to.
    public FloorMapTimeline getTimeline() {
        return timeline;
    }

    /// @return The Fact List, which its controller attaches the list's controller to.
    public FloorMapFactList getFactList() {
        return factList;
    }

    /// @return The Time List, which its controller attaches the list's controller to.
    public FloorMapTimeList getTimeList() {
        return timeList;
    }

    /// Shows or hides the dock and its splitter, keeping the width it was dragged to.
    ///
    /// @param visible Whether the dock shows.
    public void setDockVisible(final boolean visible) {
        topSplitPane.setPaneHidden(dock, !visible);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapEditorTab> {

    }
}
