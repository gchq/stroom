/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.editors;

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

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `SolrIndexModule`
/// (`stroom/search/solr/client/gin/SolrIndexModule.java`),
/// without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class SolrIndexScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
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

