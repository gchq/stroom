/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.client.gin;

import stroom.datagen.client.presenter.DataGenSettingsPresenter;
import stroom.datagen.client.presenter.DataGenSettingsPresenter.DataGenSettingsView;
import stroom.datagen.client.presenter.ScheduledProcessingPresenter.ScheduledProcessingView;
import stroom.core.client.gin.PluginModule;
import stroom.datagen.client.DataGenPlugin;
import stroom.datagen.client.presenter.DataGenPresenter;
import stroom.datagen.client.presenter.ScheduledProcessEditPresenter;
import stroom.datagen.client.presenter.ScheduledProcessEditView;
import stroom.datagen.client.presenter.ScheduledProcessingPresenter;
import stroom.datagen.client.view.DataGenSettingsViewImpl;
import stroom.datagen.client.view.ScheduledProcessEditViewImpl;
import stroom.datagen.client.view.ScheduledProcessingViewImpl;

public class DataGenModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(DataGenPlugin.class);

        bind(DataGenPresenter.class);

        bindPresenterWidget(DataGenSettingsPresenter.class,
                DataGenSettingsView.class,
                DataGenSettingsViewImpl.class);
        bindPresenterWidget(ScheduledProcessEditPresenter.class,
                ScheduledProcessEditView.class,
                ScheduledProcessEditViewImpl.class);
        bindPresenterWidget(ScheduledProcessingPresenter.class,
                ScheduledProcessingView.class,
                ScheduledProcessingViewImpl.class);
    }
}
