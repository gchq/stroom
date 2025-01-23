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

package stroom.planb.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.planb.client.PlanBPlugin;
import stroom.planb.client.presenter.PlanBPresenter;
import stroom.planb.client.presenter.PlanBSettingsPresenter;
import stroom.planb.client.presenter.PlanBSettingsPresenter.PlanBSettingsView;
import stroom.planb.client.view.PlanBSettingsViewImpl;

public class PlanBModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(PlanBPlugin.class);
        bind(PlanBPresenter.class);
        bindPresenterWidget(PlanBSettingsPresenter.class,
                PlanBSettingsView.class,
                PlanBSettingsViewImpl.class);
    }
}
