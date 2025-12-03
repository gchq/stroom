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

package stroom.task.client.presenter;

import stroom.task.client.presenter.UserTaskPresenter.UserTaskView;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.ModelStringUtil;

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class UserTaskPresenter extends MyPresenterWidget<UserTaskView> {

    @Inject
    public UserTaskPresenter(final EventBus eventBus, final UserTaskView view) {
        super(eventBus, view);
    }

    public void setTaskProgress(final TaskProgress taskProgress) {
        getView().setTaskProgress(taskProgress);
        getView().getTaskAge().setText(ModelStringUtil.formatDurationString(taskProgress.getAgeMs()));
        getView().getTaskStatus().setText(taskProgress.getTaskInfo());
    }

    public void setTerminateVisible(final boolean visible) {
        getView().setTerminateVisible(visible);
    }

    public void setUiHandlers(final UserTaskUiHandlers uiHandlers) {
        getView().setUiHandlers(uiHandlers);
    }

    public interface UserTaskView extends View, HasUiHandlers<UserTaskUiHandlers> {

        void setTaskProgress(TaskProgress taskProgress);

        HasText getTaskAge();

        HasText getTaskStatus();

        void setTerminateVisible(boolean visible);
    }
}
