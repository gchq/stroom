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

import stroom.analytics.client.ReportPlugin;
import stroom.analytics.client.presenter.ReportPresenter;
import stroom.analytics.client.presenter.ReportSettingsPresenter;
import stroom.analytics.client.presenter.ReportSettingsPresenter.ReportSettingsView;
import stroom.analytics.client.view.ReportSettingsViewImpl;
import stroom.core.client.gin.PluginModule;

public class ReportModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(ReportPlugin.class);
        bind(ReportPresenter.class);

        bindPresenterWidget(ReportSettingsPresenter.class, ReportSettingsView.class, ReportSettingsViewImpl.class);
    }
}
