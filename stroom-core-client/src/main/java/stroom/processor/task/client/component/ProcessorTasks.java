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

package stroom.processor.task.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// Processor tasks: their summary above the tasks of the summary row selected, each a list its
/// controller shows here. A document's Processor Tasks tab, and a processor filter's Tasks tab.
public class ProcessorTasks extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane splitPane;
    @UiField
    ProcessorTaskSummaryList summary;
    @UiField
    ProcessorTaskList tasks;

    /// Makes the panes, with their lists empty until their controllers fill them.
    public ProcessorTasks() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Sets the height of the summary's pane, 400px unless set; the tasks' pane takes the rest.
    ///
    /// @param size The summary pane's height, e.g. `200px`.
    public void setSummarySize(final String size) {
        splitPane.setSizes(size + ", *");
    }

    /// @return The summary of the tasks, which its controller attaches the list's controller to.
    public ProcessorTaskSummaryList getSummary() {
        return summary;
    }

    /// @return The tasks, which its controller attaches the list's controller to.
    public ProcessorTaskList getTasks() {
        return tasks;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorTasks> {

    }
}
