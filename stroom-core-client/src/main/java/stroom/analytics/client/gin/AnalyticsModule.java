/*
 * Copyright 2023 Crown Copyright
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
import stroom.analytics.client.presenter.AbstractNotificationPresenter.AnalyticNotificationView;
import stroom.analytics.client.presenter.AbstractProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.client.presenter.AnalyticRulePresenter;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter.ScheduledProcessingView;
import stroom.analytics.client.presenter.StreamingProcessingPresenter;
import stroom.analytics.client.presenter.StreamingProcessingPresenter.StreamingProcessingView;
import stroom.analytics.client.view.AnalyticDataShardsViewImpl;
import stroom.analytics.client.view.AnalyticNotificationViewImpl;
import stroom.analytics.client.view.AnalyticProcessingViewImpl;
import stroom.analytics.client.view.ScheduledProcessingViewImpl;
import stroom.analytics.client.view.StreamingProcessingViewImpl;
import stroom.core.client.gin.PluginModule;

public class AnalyticsModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AnalyticsPlugin.class);

        bind(AnalyticRulePresenter.class);
        bindPresenterWidget(AnalyticDataShardsPresenter.class,
                AnalyticDataShardsView.class,
                AnalyticDataShardsViewImpl.class);
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
                AnalyticNotificationView.class,
                AnalyticNotificationViewImpl.class);

    }
}
