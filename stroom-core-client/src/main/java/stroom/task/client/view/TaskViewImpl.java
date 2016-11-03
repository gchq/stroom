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

package stroom.task.client.view;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import stroom.task.client.presenter.TaskPresenter.TaskView;
import stroom.task.client.presenter.TaskUiHandlers;
import stroom.util.shared.TaskId;
import stroom.widget.button.client.ImageButton;

public class TaskViewImpl extends ViewWithUiHandlers<TaskUiHandlers>implements TaskView {
    public interface Binder extends UiBinder<Widget, TaskViewImpl> {
    }

    private final Widget widget;

    @UiField
    HorizontalPanel layout;
    @UiField
    Label taskName;
    @UiField
    Label taskStatus;
    @UiField
    Label taskAge;
    @UiField
    ImageButton terminate;

    private TaskId id;
    private String taskNameString;

    @Inject
    public TaskViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        layout.setCellWidth(terminate, "18px");
        layout.setCellVerticalAlignment(terminate, HorizontalPanel.ALIGN_MIDDLE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTaskName(final String taskName) {
        this.taskName.setText(taskName);
        this.taskNameString = taskName;
    }

    @Override
    public HasText getTaskStatus() {
        return taskStatus;
    }

    @Override
    public HasText getTaskAge() {
        return taskAge;
    }

    @Override
    public void setTerminateVisible(final boolean visible) {
        terminate.setVisible(visible);
    }

    @Override
    public void setId(final TaskId id) {
        this.id = id;
    }

    @UiHandler("terminate")
    public void onTerminateClick(final ClickEvent event) {
        if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
            if (getUiHandlers() != null) {
                getUiHandlers().onTerminate(id, taskNameString);
            }
        }
    }
}
