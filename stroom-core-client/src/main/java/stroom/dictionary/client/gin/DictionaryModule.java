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

package stroom.dictionary.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.dictionary.client.DictionaryPlugin;
import stroom.dictionary.client.presenter.DictionaryPresenter;
import stroom.dictionary.client.presenter.DictionarySettingsPresenter;
import stroom.dictionary.client.presenter.DictionarySettingsPresenter.DictionarySettingsView;
import stroom.dictionary.client.view.DictionarySettingsViewImpl;

public class DictionaryModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(DictionaryPlugin.class);
        bind(DictionaryPresenter.class);
        bindPresenterWidget(DictionarySettingsPresenter.class,
                DictionarySettingsView.class,
                DictionarySettingsViewImpl.class);
    }
}
