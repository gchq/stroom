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

package stroom.alert.client.gin;

import stroom.alert.client.AlertPlugin;
import stroom.alert.client.presenter.CommonAlertPresenter;
import stroom.alert.client.presenter.CommonAlertPresenter.CommonAlertView;
import stroom.alert.client.presenter.PromptPresenter;
import stroom.alert.client.presenter.PromptPresenter.PromptProxy;
import stroom.alert.client.presenter.PromptPresenter.PromptView;
import stroom.alert.client.view.CommonAlertViewImpl;
import stroom.alert.client.view.PromptViewImpl;
import stroom.core.client.gin.PluginModule;

public class AlertModule extends PluginModule {

    @Override
    protected void configure() {
        bind(AlertPlugin.class).asEagerSingleton();

        bindPresenter(PromptPresenter.class, PromptView.class, PromptViewImpl.class, PromptProxy.class);
        bindPresenterWidget(CommonAlertPresenter.class, CommonAlertView.class, CommonAlertViewImpl.class);
    }
}
