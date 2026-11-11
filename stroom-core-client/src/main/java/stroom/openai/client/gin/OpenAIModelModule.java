/*
 * Copyright 2025 Crown Copyright
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
import stroom.openai.client.presenter.McpToolListPresenter;
import stroom.openai.client.presenter.McpToolListPresenter.McpToolListView;
import stroom.openai.client.presenter.McpToolServerEditPresenter;
import stroom.openai.client.presenter.McpToolServerEditPresenter.McpToolServerEditView;
import stroom.openai.client.presenter.OpenAIModelMcpToolServersPresenter;
import stroom.openai.client.presenter.OpenAIModelMcpToolServersPresenter.McpToolServersView;
import stroom.openai.client.presenter.OpenAIModelPresenter;
import stroom.openai.client.presenter.OpenAIModelSettingsPresenter;
import stroom.openai.client.presenter.OpenAIModelSettingsPresenter.OpenAIModelSettingsView;
import stroom.openai.client.view.McpToolListViewImpl;
import stroom.openai.client.view.McpToolServerEditViewImpl;
import stroom.openai.client.view.McpToolServersViewImpl;
import stroom.openai.client.view.OpenAIModelSettingsViewImpl;

public class OpenAIModelModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(OpenAIModelPlugin.class);
        bind(OpenAIModelPresenter.class);
        bind(OpenAIModelMcpToolServersPresenter.class);
        bind(McpToolServersView.class).to(McpToolServersViewImpl.class);
        bindPresenterWidget(McpToolListPresenter.class,
                McpToolListView.class,
                McpToolListViewImpl.class);
        bindPresenterWidget(OpenAIModelSettingsPresenter.class,
                OpenAIModelSettingsView.class,
                OpenAIModelSettingsViewImpl.class);
        bindPresenterWidget(McpToolServerEditPresenter.class,
                McpToolServerEditView.class,
                McpToolServerEditViewImpl.class);
    }
}
