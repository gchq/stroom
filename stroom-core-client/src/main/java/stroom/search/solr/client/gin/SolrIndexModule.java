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

package stroom.search.solr.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.search.solr.client.SolrIndexPlugin;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter.SolrIndexFieldEditView;
import stroom.search.solr.client.presenter.SolrIndexFieldListPresenter;
import stroom.search.solr.client.presenter.SolrIndexFieldListPresenter.SolrIndexFieldListView;
import stroom.search.solr.client.presenter.SolrIndexPresenter;
import stroom.search.solr.client.presenter.SolrIndexSettingsPresenter;
import stroom.search.solr.client.presenter.SolrIndexSettingsPresenter.SolrIndexSettingsView;
import stroom.search.solr.client.view.SolrIndexFieldEditViewImpl;
import stroom.search.solr.client.view.SolrIndexFieldListViewImpl;
import stroom.search.solr.client.view.SolrIndexSettingsViewImpl;

public class SolrIndexModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(SolrIndexPlugin.class);
        bind(SolrIndexPresenter.class);
        bindPresenterWidget(SolrIndexSettingsPresenter.class,
                SolrIndexSettingsView.class,
                SolrIndexSettingsViewImpl.class);
        bindPresenterWidget(SolrIndexFieldListPresenter.class,
                SolrIndexFieldListView.class,
                SolrIndexFieldListViewImpl.class);
        bindPresenterWidget(SolrIndexFieldEditPresenter.class,
                SolrIndexFieldEditView.class,
                SolrIndexFieldEditViewImpl.class);
    }
}
