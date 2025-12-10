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

package stroom.state.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.state.client.ScyllaDbPlugin;
import stroom.state.client.presenter.ScyllaDbPresenter;
import stroom.state.client.presenter.ScyllaDbSettingsPresenter;
import stroom.state.client.presenter.ScyllaDbSettingsPresenter.ScyllaDbSettingsView;
import stroom.state.client.view.ScyllaDbSettingsViewImpl;

public class ScyllaDbModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(ScyllaDbPlugin.class);
        bind(ScyllaDbPresenter.class);
        bindPresenterWidget(ScyllaDbSettingsPresenter.class,
                ScyllaDbSettingsView.class,
                ScyllaDbSettingsViewImpl.class);
    }
}
