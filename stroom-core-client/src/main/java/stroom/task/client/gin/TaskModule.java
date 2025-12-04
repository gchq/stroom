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

package stroom.task.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.task.client.presenter.UserTaskManagerPresenter;
import stroom.task.client.presenter.UserTaskManagerPresenter.UserTaskManagerProxy;
import stroom.task.client.presenter.UserTaskManagerPresenter.UserTaskManagerView;
import stroom.task.client.presenter.UserTaskPresenter;
import stroom.task.client.presenter.UserTaskPresenter.UserTaskView;
import stroom.task.client.view.UserTaskManagerViewImpl;
import stroom.task.client.view.UserTaskViewImpl;

public class TaskModule extends PluginModule {

    @Override
    protected void configure() {
        bindPresenter(UserTaskManagerPresenter.class, UserTaskManagerView.class, UserTaskManagerViewImpl.class,
                UserTaskManagerProxy.class);
        bindPresenterWidget(UserTaskPresenter.class, UserTaskView.class, UserTaskViewImpl.class);
    }
}
