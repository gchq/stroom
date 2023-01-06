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

package stroom.alert.rule.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.alert.rule.client.AlertRulePlugin;
import stroom.alert.rule.client.presenter.AlertRulePresenter;
import stroom.alert.rule.client.presenter.AlertRuleSettingsPresenter;
import stroom.alert.rule.client.presenter.AlertRuleSettingsPresenter.AlertRuleSettingsView;
import stroom.alert.rule.client.view.AlertRuleSettingsViewImpl;

public class AlertRuleModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AlertRulePlugin.class);

        bind(AlertRulePresenter.class);

        bindSharedView(AlertRuleSettingsView.class, AlertRuleSettingsViewImpl.class);
        bind(AlertRuleSettingsPresenter.class);
    }
}
