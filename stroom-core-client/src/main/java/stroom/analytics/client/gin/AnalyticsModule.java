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

package stroom.analytics.client.gin;

import stroom.analytics.client.AnalyticsPlugin;
import stroom.analytics.client.presenter.AbstractDuplicateManagementPresenter.DuplicateManagementView;
import stroom.analytics.client.presenter.AbstractProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.client.presenter.AnalyticEmailDestinationPresenter;
import stroom.analytics.client.presenter.AnalyticEmailDestinationPresenter.AnalyticEmailDestinationView;
import stroom.analytics.client.presenter.AnalyticNotificationEditPresenter;
import stroom.analytics.client.presenter.AnalyticNotificationEditPresenter.AnalyticNotificationEditView;
import stroom.analytics.client.presenter.AnalyticRulePresenter;
import stroom.analytics.client.presenter.AnalyticStreamDestinationPresenter;
import stroom.analytics.client.presenter.AnalyticStreamDestinationPresenter.AnalyticStreamDestinationView;
import stroom.analytics.client.presenter.ScheduledProcessEditPresenter;
import stroom.analytics.client.presenter.ScheduledProcessEditView;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter.ScheduledProcessingView;
import stroom.analytics.client.presenter.StreamingProcessingPresenter;
import stroom.analytics.client.presenter.StreamingProcessingPresenter.StreamingProcessingView;
import stroom.analytics.client.presenter.TableBuilderProcessingPresenter;
import stroom.analytics.client.presenter.TableBuilderProcessingPresenter.TableBuilderProcessingView;
import stroom.analytics.client.view.AnalyticDataShardsViewImpl;
import stroom.analytics.client.view.AnalyticEmailDestinationViewImpl;
import stroom.analytics.client.view.AnalyticNotificationEditViewImpl;
import stroom.analytics.client.view.AnalyticProcessingViewImpl;
import stroom.analytics.client.view.AnalyticStreamDestinationViewImpl;
import stroom.analytics.client.view.DuplicateManagementViewImpl;
import stroom.analytics.client.view.ScheduledProcessEditViewImpl;
import stroom.analytics.client.view.ScheduledProcessingViewImpl;
import stroom.analytics.client.view.StreamingProcessingViewImpl;
import stroom.analytics.client.view.TableBuilderProcessingViewImpl;
import stroom.core.client.gin.PluginModule;

public class AnalyticsModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AnalyticsPlugin.class);

        bind(AnalyticRulePresenter.class);

        bindPresenterWidget(AnalyticNotificationEditPresenter.class,
                AnalyticNotificationEditView.class,
                AnalyticNotificationEditViewImpl.class);
        bindPresenterWidget(AnalyticEmailDestinationPresenter.class,
                AnalyticEmailDestinationView.class,
                AnalyticEmailDestinationViewImpl.class);
        bindPresenterWidget(AnalyticStreamDestinationPresenter.class,
                AnalyticStreamDestinationView.class,
                AnalyticStreamDestinationViewImpl.class);
        bindPresenterWidget(AnalyticDataShardsPresenter.class,
                AnalyticDataShardsView.class,
                AnalyticDataShardsViewImpl.class);
        bindPresenterWidget(ScheduledProcessEditPresenter.class,
                ScheduledProcessEditView.class,
                ScheduledProcessEditViewImpl.class);
        bindPresenterWidget(TableBuilderProcessingPresenter.class,
                TableBuilderProcessingView.class,
                TableBuilderProcessingViewImpl.class);
        bindPresenterWidget(StreamingProcessingPresenter.class,
                StreamingProcessingView.class,
                StreamingProcessingViewImpl.class);
        bindPresenterWidget(ScheduledProcessingPresenter.class,
                ScheduledProcessingView.class,
                ScheduledProcessingViewImpl.class);

        bindSharedView(
                AnalyticProcessingView.class,
                AnalyticProcessingViewImpl.class);
        bindSharedView(
                DuplicateManagementView.class,
                DuplicateManagementViewImpl.class);

    }
}
