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

import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter.ElasticIndexFieldListView;
import stroom.search.elastic.client.presenter.ElasticIndexPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.client.view.ElasticIndexFieldListViewImpl;
import stroom.search.elastic.client.view.ElasticIndexSettingsViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `ElasticIndexModule`
/// (`stroom/search/elastic/client/gin/ElasticIndexModule.java`),
/// without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class ElasticIndexScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(ElasticIndexPresenter.class);
        bindPresenterWidget(ElasticIndexSettingsPresenter.class,
                ElasticIndexSettingsView.class,
                ElasticIndexSettingsViewImpl.class);
        bindPresenterWidget(ElasticIndexFieldListPresenter.class,
                ElasticIndexFieldListView.class,
                ElasticIndexFieldListViewImpl.class);
    }
}

