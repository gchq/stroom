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

package stroom.analytics.client.gin;

import stroom.analytics.client.AnalyticsPlugin;
import stroom.analytics.client.presenter.AnalyticRulePresenter;
import stroom.analytics.client.presenter.AnalyticRuleProcessingPresenter;
import stroom.analytics.client.presenter.AnalyticRuleProcessingPresenter.AlertRuleProcessingView;
import stroom.analytics.client.presenter.AnalyticRuleSettingsPresenter;
import stroom.analytics.client.presenter.AnalyticRuleSettingsPresenter.AlertRuleSettingsView;
import stroom.analytics.client.view.AnalyticRuleProcessingViewImpl;
import stroom.analytics.client.view.AnalyticRuleSettingsViewImpl;
import stroom.core.client.gin.PluginModule;

public class AnalyticsModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AnalyticsPlugin.class);

        bind(AnalyticRulePresenter.class);

        bindPresenterWidget(AnalyticRuleSettingsPresenter.class,
                AlertRuleSettingsView.class,
                AnalyticRuleSettingsViewImpl.class);
        bindPresenterWidget(AnalyticRuleProcessingPresenter.class,
                AlertRuleProcessingView.class,
                AnalyticRuleProcessingViewImpl.class);
    }
}
