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

package stroom.script.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.script.client.ScriptCache;
import stroom.script.client.ScriptPlugin;
import stroom.script.client.presenter.ScriptPresenter;
import stroom.script.client.presenter.ScriptSettingsPresenter;
import stroom.script.client.presenter.ScriptSettingsPresenter.ScriptSettingsView;
import stroom.script.client.view.ScriptSettingsViewImpl;

import com.google.inject.Singleton;

public class ScriptModule extends PluginModule {

    @Override
    protected void configure() {
        bind(ScriptCache.class).in(Singleton.class);

        bindPlugin(ScriptPlugin.class);
        bind(ScriptPresenter.class);
        bindPresenterWidget(ScriptSettingsPresenter.class, ScriptSettingsView.class, ScriptSettingsViewImpl.class);
    }
}
