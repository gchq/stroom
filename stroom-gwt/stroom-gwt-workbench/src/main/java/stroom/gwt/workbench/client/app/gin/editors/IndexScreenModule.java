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

import stroom.index.client.presenter.DenseVectorFieldPresenter;
import stroom.index.client.presenter.DenseVectorFieldPresenter.DenseVectorFieldView;
import stroom.index.client.presenter.IndexFieldEditPresenter;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.client.presenter.IndexSettingsPresenter;
import stroom.index.client.presenter.IndexSettingsPresenter.IndexSettingsView;
import stroom.index.client.presenter.IndexVolumeEditPresenter;
import stroom.index.client.presenter.IndexVolumeGroupEditPresenter;
import stroom.index.client.presenter.IndexVolumeGroupEditPresenter.IndexVolumeGroupEditView;
import stroom.index.client.presenter.IndexVolumeGroupPresenter;
import stroom.index.client.view.DenseVectorFieldViewImpl;
import stroom.index.client.view.IndexFieldEditViewImpl;
import stroom.index.client.view.IndexSettingsViewImpl;
import stroom.index.client.view.IndexVolumeEditViewImpl;
import stroom.index.client.view.IndexVolumeGroupEditViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `IndexModule`
/// (`stroom/index/client/gin/IndexModule.java`),
/// without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class IndexScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(IndexPresenter.class);
        bind(IndexVolumeGroupPresenter.class);
        bindPresenterWidget(IndexVolumeGroupEditPresenter.class,
                IndexVolumeGroupEditView.class,
                IndexVolumeGroupEditViewImpl.class);
        bindPresenterWidget(IndexVolumeEditPresenter.class,
                IndexVolumeEditPresenter.IndexVolumeEditView.class,
                IndexVolumeEditViewImpl.class);
        bindPresenterWidget(IndexSettingsPresenter.class,
                IndexSettingsView.class,
                IndexSettingsViewImpl.class);
        bindPresenterWidget(IndexFieldEditPresenter.class,
                IndexFieldEditView.class,
                IndexFieldEditViewImpl.class);
        bindPresenterWidget(DenseVectorFieldPresenter.class,
                DenseVectorFieldView.class,
                DenseVectorFieldViewImpl.class);
    }
}

