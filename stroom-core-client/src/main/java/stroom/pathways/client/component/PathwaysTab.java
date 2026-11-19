/*
 * Copyright 2016 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A pathways document's Pathways tab: its pathways above the paths of the one selected, each
/// shown here by its controller.
public class PathwaysTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PathwayList pathwayList;
    @UiField
    PathwayTree pathwayTree;

    /// Makes the panes, with nothing in them.
    public PathwaysTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The pathways, which its controller attaches the list's controller to.
    public PathwayList getPathwayList() {
        return pathwayList;
    }

    /// @return The paths of the pathway selected, which its controller attaches the graph's
    ///         controller to.
    public PathwayTree getPathwayTree() {
        return pathwayTree;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PathwaysTab> {

    }
}
