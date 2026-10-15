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

import stroom.config.global.client.presenter.ConfigPropertyClusterValuesListPresenter;
import stroom.config.global.client.presenter.ConfigPropertyClusterValuesPresenter;
import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyEditPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyListPresenter;
import stroom.config.global.client.view.ConfigPropertyClusterValuesViewImpl;
import stroom.config.global.client.view.GlobalPropertyEditViewImpl;
import stroom.config.global.client.view.GlobalPropertyTabViewImpl;
import stroom.job.client.presenter.JobPresenter;
import stroom.job.client.presenter.JobPresenter.JobView;
import stroom.job.client.view.JobViewImpl;
import stroom.node.client.presenter.NodeGroupEditPresenter;
import stroom.node.client.presenter.NodeGroupEditPresenter.NodeGroupEditView;
import stroom.node.client.presenter.NodePresenter;
import stroom.node.client.presenter.NodePresenter.NodeView;
import stroom.node.client.view.NodeGroupEditViewImpl;
import stroom.node.client.view.NodeViewImpl;
import stroom.schedule.client.SchedulePopup;
import stroom.schedule.client.SchedulePopup.ScheduleView;
import stroom.schedule.client.ScheduleViewImpl;
import stroom.task.client.presenter.TaskManagerPresenter;
import stroom.task.client.presenter.TaskManagerPresenter.TaskManagerView;
import stroom.task.client.view.TaskManagerViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `MonitoringModule`
/// (`stroom/monitoring/client/gin/MonitoringModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class MonitoringScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(SchedulePopup.class,
                ScheduleView.class,
                ScheduleViewImpl.class);
        bindPresenterWidget(NodePresenter.class,
                NodeView.class,
                NodeViewImpl.class);
        bindPresenterWidget(NodeGroupEditPresenter.class,
                NodeGroupEditView.class,
                NodeGroupEditViewImpl.class);
        bindPresenterWidget(JobPresenter.class,
                JobView.class,
                JobViewImpl.class);
        bind(ManageGlobalPropertyListPresenter.class);
        bind(ConfigPropertyClusterValuesListPresenter.class);
        bindPresenterWidget(GlobalPropertyTabPresenter.class,
                GlobalPropertyTabPresenter.GlobalPropertyTabView.class,
                GlobalPropertyTabViewImpl.class);
        bindPresenterWidget(ManageGlobalPropertyEditPresenter.class,
                ManageGlobalPropertyEditPresenter.GlobalPropertyEditView.class,
                GlobalPropertyEditViewImpl.class);
        bindPresenterWidget(ConfigPropertyClusterValuesPresenter.class,
                ConfigPropertyClusterValuesPresenter.ConfigPropertyClusterValuesView.class,
                ConfigPropertyClusterValuesViewImpl.class);
        bindPresenterWidget(TaskManagerPresenter.class,
                TaskManagerView.class,
                TaskManagerViewImpl.class);
    }
}

