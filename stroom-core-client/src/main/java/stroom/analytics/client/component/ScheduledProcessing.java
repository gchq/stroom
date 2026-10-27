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
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A document's scheduled executions: its schedules above the history of the one selected, each a
/// list its controller shows here.
public class ScheduledProcessing extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel scheduleList;
    @UiField
    SimplePanel historyList;

    /// Makes the panes, with no lists.
    public ScheduledProcessing() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param view The schedules.
    public void setScheduleList(final IsWidget view) {
        scheduleList.setWidget(view);
    }

    /// @param view The history of the schedule selected.
    public void setHistoryList(final IsWidget view) {
        historyList.setWidget(view);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ScheduledProcessing> {

    }
}
