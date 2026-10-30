/*
 * Copyright 2022 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A document's scheduled executions: its schedules above the history of the one selected, each a
/// list its controller shows here.
public class ScheduledProcessing extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ScheduledProcessList scheduleList;
    @UiField
    ScheduledProcessHistoryList historyList;

    /// Makes the panes, with no lists.
    public ScheduledProcessing() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The schedules, which its controller attaches the list's controller to.
    public ScheduledProcessList getScheduleList() {
        return scheduleList;
    }

    /// @return The history of the schedule selected, which its controller attaches the list's
    ///         controller to.
    public ScheduledProcessHistoryList getHistoryList() {
        return historyList;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ScheduledProcessing> {

    }
}
