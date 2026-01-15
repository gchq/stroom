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

package stroom.openai.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.openai.client.OpenAIModelPlugin;
import stroom.openai.client.presenter.OpenAIModelPresenter;
import stroom.openai.client.presenter.OpenAIModelSettingsPresenter;
import stroom.openai.client.presenter.OpenAIModelSettingsPresenter.OpenAIModelSettingsView;
import stroom.openai.client.view.OpenAIModelSettingsViewImpl;

public class OpenAIModelModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(OpenAIModelPlugin.class);
        bind(OpenAIModelPresenter.class);
        bindPresenterWidget(OpenAIModelSettingsPresenter.class,
                OpenAIModelSettingsView.class,
                OpenAIModelSettingsViewImpl.class);
    }
}
