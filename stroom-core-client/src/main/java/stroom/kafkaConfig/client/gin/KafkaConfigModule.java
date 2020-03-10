/*
 * Copyright 2019 Crown Copyright
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

package stroom.kafkaConfig.client.gin;


import stroom.core.client.gin.PluginModule;
import stroom.kafkaConfig.client.KafkaConfigPlugin;
import stroom.kafkaConfig.client.presenter.KafkaConfigPresenter;
import stroom.kafkaConfig.client.presenter.KafkaConfigSettingsPresenter;
import stroom.kafkaConfig.client.presenter.KafkaConfigSettingsPresenter.KafkaConfigSettingsView;
import stroom.kafkaConfig.client.view.KafkaConfigSettingsViewImpl;

public class KafkaConfigModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(KafkaConfigPlugin.class);
        bind(KafkaConfigPresenter.class);
        bindPresenterWidget(KafkaConfigSettingsPresenter.class, KafkaConfigSettingsView.class, KafkaConfigSettingsViewImpl.class);
    }
}
