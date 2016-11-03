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

package stroom.task.client.gin;

import stroom.app.client.gin.PluginModule;
import stroom.task.client.presenter.TaskManagerPresenter;
import stroom.task.client.presenter.TaskManagerPresenter.TaskManagerProxy;
import stroom.task.client.presenter.TaskManagerPresenter.TaskManagerView;
import stroom.task.client.presenter.TaskPresenter;
import stroom.task.client.presenter.TaskPresenter.TaskView;
import stroom.task.client.view.TaskManagerViewImpl;
import stroom.task.client.view.TaskViewImpl;

public class TaskModule extends PluginModule {
    @Override
    protected void configure() {
        bindPresenter(TaskManagerPresenter.class, TaskManagerView.class, TaskManagerViewImpl.class,
                TaskManagerProxy.class);
        bindPresenterWidget(TaskPresenter.class, TaskView.class, TaskViewImpl.class);
    }
}
