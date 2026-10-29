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

/// A Traces store's Traces tab: its traces (a list its controller shows here) above the selected
/// trace's spans.
public class TracesListTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TracesList tracesList;
    @UiField
    TraceOverviewChart traceOverview;

    /// Makes the tab, with no traces.
    public TracesListTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The traces, which its controller attaches the list's controller to.
    public TracesList getTracesList() {
        return tracesList;
    }

    /// @return The selected trace's spans, which its controller fills.
    public TraceOverviewChart getTraceOverview() {
        return traceOverview;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TracesListTab> {

    }
}
