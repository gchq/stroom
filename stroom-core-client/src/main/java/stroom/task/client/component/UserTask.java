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

package stroom.task.client.component;

import stroom.task.shared.TaskProgress;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A task of the user's in the task manager: its name, age and status, with a button that asks to
/// terminate it. Its markup is `UserTask.ui.xml`.
public class UserTask extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label taskName;
    @UiField
    Label taskAge;
    @UiField
    Label taskStatus;
    @UiField
    ToolbarButton terminate;

    public UserTask() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param taskProgress The task, as it is now.
    public void setTaskProgress(final TaskProgress taskProgress) {
        taskName.setText(taskProgress.getTaskName());
        taskAge.setText(ModelStringUtil.formatDurationString(taskProgress.getAgeMs()));
        taskStatus.setText(taskProgress.getTaskInfo());
    }

    /// @param onTerminate Run when the user asks to terminate the task, which shows the button; null
    ///                    hides it.
    public void setOnTerminate(final Runnable onTerminate) {
        terminate.setOnClick(onTerminate);
        terminate.setVisible(onTerminate != null);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserTask> {

    }
}
