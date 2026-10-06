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

package stroom.gwt.workbench.client.app.gin.editors;

import stroom.analytics.client.presenter.BatchExecutionScheduleEditPresenter;
import stroom.analytics.client.presenter.BatchExecutionScheduleEditPresenter.BatchExecutionScheduleEditView;
import stroom.analytics.client.presenter.ExecutionScheduleRunNowPresenter;
import stroom.analytics.client.presenter.ExecutionScheduleRunNowPresenter.ExecutionScheduleRunNowView;
import stroom.analytics.client.presenter.ScheduledProcessEditPresenter;
import stroom.analytics.client.presenter.ScheduledProcessEditView;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter.ScheduledProcessingView;
import stroom.analytics.client.view.BatchExecutionScheduleEditViewImpl;
import stroom.analytics.client.view.ExecutionScheduleRunNowViewImpl;
import stroom.analytics.client.view.ScheduledProcessEditViewImpl;
import stroom.analytics.client.view.ScheduledProcessingViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `AnalyticsModule`
/// (`stroom/analytics/client/gin/AnalyticsModule.java`) for execution schedules, without its
/// plugins, app services and analytic rule screens.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class ScheduleScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(ScheduledProcessEditPresenter.class,
                ScheduledProcessEditView.class,
                ScheduledProcessEditViewImpl.class);
        bindPresenterWidget(ScheduledProcessingPresenter.class,
                ScheduledProcessingView.class,
                ScheduledProcessingViewImpl.class);
        bindPresenterWidget(BatchExecutionScheduleEditPresenter.class,
                BatchExecutionScheduleEditView.class,
                BatchExecutionScheduleEditViewImpl.class);
        bindPresenterWidget(ExecutionScheduleRunNowPresenter.class,
                ExecutionScheduleRunNowView.class,
                ExecutionScheduleRunNowViewImpl.class);
    }
}

