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

package stroom.gwt.workbench.client.app.gin.processing;

import stroom.analytics.client.presenter.BatchExecutionScheduleEditPresenter;
import stroom.analytics.client.presenter.BatchExecutionScheduleEditPresenter.BatchExecutionScheduleEditView;
import stroom.analytics.client.presenter.ExecutionScheduleRunNowPresenter;
import stroom.analytics.client.presenter.ExecutionScheduleRunNowPresenter.ExecutionScheduleRunNowView;
import stroom.analytics.client.presenter.ScheduledProcessEditPresenter;
import stroom.analytics.client.presenter.ScheduledProcessEditView;
import stroom.analytics.client.view.BatchExecutionScheduleEditViewImpl;
import stroom.analytics.client.view.ExecutionScheduleRunNowViewImpl;
import stroom.analytics.client.view.ScheduledProcessEditViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The execution schedule bindings of Stroom's `AnalyticsModule`
/// (`stroom/analytics/client/gin/AnalyticsModule.java`): the schedule editor, batch editor and
/// 'Run Now' dialog that the Execution Schedule Manager uses, without the analytic rule editor
/// (whose graph needs the query and dashboard modules) or the module's plugin.
public class AnalyticsScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(ScheduledProcessEditPresenter.class,
                ScheduledProcessEditView.class,
                ScheduledProcessEditViewImpl.class);
        bindPresenterWidget(BatchExecutionScheduleEditPresenter.class,
                BatchExecutionScheduleEditView.class,
                BatchExecutionScheduleEditViewImpl.class);
        bindPresenterWidget(ExecutionScheduleRunNowPresenter.class,
                ExecutionScheduleRunNowView.class,
                ExecutionScheduleRunNowViewImpl.class);
    }
}

