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
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A floor map's Map tab: the map beside the right-hand dock, above the timeline (all shown here by
/// its controller).
public class FloorMapMap extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane topSplitPane;
    @UiField
    SimplePanel mapPanel;
    @UiField
    LinkTabPanel dockPanel;
    @UiField
    SimplePanel timelinePanel;

    /// Makes the tab, with nothing in it.
    public FloorMapMap() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param map The map.
    public void setMap(final IsWidget map) {
        mapPanel.setWidget(map);
    }

    /// @return The right-hand dock, for its controller to attach to.
    public LinkTabPanel getDock() {
        return dockPanel;
    }

    /// @param timeline The timeline.
    public void setTimeline(final IsWidget timeline) {
        timelinePanel.setWidget(timeline);
    }

    /// Shows or hides the dock and its splitter, keeping the width it was dragged to.
    ///
    /// @param visible Whether the dock shows.
    public void setDockVisible(final boolean visible) {
        topSplitPane.setPaneHidden(dockPanel, !visible);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapMap> {

    }
}
