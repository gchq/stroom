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

package stroom.data.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A document's Processor Tasks tab: a summary of its tasks, and the tasks of the selected row.
public class ProcessorTasksTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ProcessorTaskSummaryList taskSummary;
    @UiField
    ProcessorTasksList taskList;

    /// Makes the screen, with its lists empty until their controllers fill them.
    public ProcessorTasksTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The summary of tasks, which its controller attaches the list's controller to.
    public ProcessorTaskSummaryList getTaskSummary() {
        return taskSummary;
    }

    /// @return The tasks, which its controller attaches the list's controller to.
    public ProcessorTasksList getTaskList() {
        return taskList;
    }

    interface Binder extends UiBinder<Widget, ProcessorTasksTab> {

    }
}
