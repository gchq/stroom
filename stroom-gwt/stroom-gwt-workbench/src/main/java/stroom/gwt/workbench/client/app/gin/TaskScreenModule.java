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

package stroom.gwt.workbench.client.app.gin;

import stroom.task.client.presenter.UserTaskManagerPresenter;
import stroom.task.client.presenter.UserTaskManagerPresenter.UserTaskManagerProxy;
import stroom.task.client.presenter.UserTaskManagerPresenter.UserTaskManagerView;
import stroom.task.client.presenter.UserTaskPresenter;
import stroom.task.client.presenter.UserTaskPresenter.UserTaskView;
import stroom.task.client.view.UserTaskManagerViewImpl;
import stroom.task.client.view.UserTaskViewImpl;

import com.google.inject.Provides;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `TaskModule`
/// (`stroom/task/client/gin/TaskModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class TaskScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(UserTaskManagerPresenter.class,
                UserTaskManagerView.class,
                UserTaskManagerViewImpl.class);
        bindPresenterWidget(UserTaskPresenter.class,
                UserTaskView.class,
                UserTaskViewImpl.class);
    }

    /// @return No proxy, see the class description.
    @Provides
    UserTaskManagerProxy provideUserTaskManagerProxy() {
        return null;
    }
}

