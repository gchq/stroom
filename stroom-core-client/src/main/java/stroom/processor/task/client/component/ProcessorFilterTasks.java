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

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A processor filter's tasks: their summary above the tasks of the summary row selected, each a
/// list its controller shows here.
public class ProcessorFilterTasks extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel summary;
    @UiField
    SimplePanel tasks;

    /// Makes the panes, with nothing in them.
    public ProcessorFilterTasks() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param summary The summary of the tasks.
    public void setSummary(final IsWidget summary) {
        this.summary.setWidget(summary);
    }

    /// @param tasks The tasks.
    public void setTasks(final IsWidget tasks) {
        this.tasks.setWidget(tasks);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorFilterTasks> {

    }
}
