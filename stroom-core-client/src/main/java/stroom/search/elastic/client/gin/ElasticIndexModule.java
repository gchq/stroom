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
import stroom.search.elastic.client.ElasticIndexPlugin;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter.ElasticIndexFieldListView;
import stroom.search.elastic.client.presenter.ElasticIndexPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.client.view.ElasticIndexFieldListViewImpl;
import stroom.search.elastic.client.view.ElasticIndexSettingsViewImpl;

public class ElasticIndexModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(ElasticIndexPlugin.class);
        bind(ElasticIndexPresenter.class);
        bindPresenterWidget(ElasticIndexSettingsPresenter.class,
                ElasticIndexSettingsView.class,
                ElasticIndexSettingsViewImpl.class);
        bindPresenterWidget(ElasticIndexFieldListPresenter.class,
                ElasticIndexFieldListView.class,
                ElasticIndexFieldListViewImpl.class);
    }
}
