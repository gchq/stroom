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

package stroom.search.elastic.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.search.elastic.client.ElasticClusterPlugin;
import stroom.search.elastic.client.presenter.ElasticClusterPresenter;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter.ElasticClusterSettingsView;
import stroom.search.elastic.client.view.ElasticClusterSettingsViewImpl;

public class ElasticClusterModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(ElasticClusterPlugin.class);
        bind(ElasticClusterPresenter.class);
        bindPresenterWidget(ElasticClusterSettingsPresenter.class,
                ElasticClusterSettingsView.class,
                ElasticClusterSettingsViewImpl.class);
    }
}
