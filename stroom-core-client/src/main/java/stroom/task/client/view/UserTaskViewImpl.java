/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.svg.shared.SvgImage;
import stroom.task.client.presenter.UserTaskPresenter.UserTaskView;
import stroom.task.client.presenter.UserTaskUiHandlers;
import stroom.task.shared.TaskProgress;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class UserTaskViewImpl extends ViewWithUiHandlers<UserTaskUiHandlers> implements UserTaskView {

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
    Button terminate;

    private TaskProgress taskProgress;

    @Inject
    public UserTaskViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        layout.setCellWidth(terminate, "18px");
        layout.setCellVerticalAlignment(terminate, HorizontalPanel.ALIGN_MIDDLE);
        SvgImageUtil.setSvgAsInnerHtml(terminate, SvgImage.STOP);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTaskProgress(final TaskProgress taskProgress) {
        this.taskName.setText(taskProgress.getTaskName());
        this.taskProgress = taskProgress;
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

    @UiHandler("terminate")
    public void onTerminateClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().onTerminate(taskProgress);
            }
        }
    }

    public interface Binder extends UiBinder<Widget, UserTaskViewImpl> {

    }
}
