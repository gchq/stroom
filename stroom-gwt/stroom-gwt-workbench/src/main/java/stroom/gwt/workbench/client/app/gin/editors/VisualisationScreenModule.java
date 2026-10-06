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

import stroom.visualisation.client.presenter.VisualisationAssetsAddItemDialogPresenter;
import stroom.visualisation.client.presenter.VisualisationAssetsAddItemDialogPresenter.VisualisationAssetsAddFolderDialogView;
import stroom.visualisation.client.presenter.VisualisationAssetsEditAssetDialogPresenter;
import stroom.visualisation.client.presenter.VisualisationAssetsEditAssetDialogPresenter.VisualisationAssetsEditAssetDialogView;
import stroom.visualisation.client.presenter.VisualisationAssetsPresenter;
import stroom.visualisation.client.presenter.VisualisationAssetsPresenter.VisualisationAssetsView;
import stroom.visualisation.client.presenter.VisualisationAssetsUploadFileDialogPresenter;
import stroom.visualisation.client.presenter.VisualisationAssetsUploadFileDialogPresenter.VisualisationAssetsUploadFileDialogView;
import stroom.visualisation.client.presenter.VisualisationPresenter;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter.VisualisationSettingsView;
import stroom.visualisation.client.view.VisualisationAssetsAddItemDialogViewImpl;
import stroom.visualisation.client.view.VisualisationAssetsEditAssetDialogViewImpl;
import stroom.visualisation.client.view.VisualisationAssetsUploadFileDialogViewImpl;
import stroom.visualisation.client.view.VisualisationAssetsViewImpl;
import stroom.visualisation.client.view.VisualisationSettingsViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `VisualisationModule`
/// (`stroom/visualisation/client/gin/VisualisationModule.java`),
/// without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class VisualisationScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(VisualisationPresenter.class);
        bindPresenterWidget(VisualisationSettingsPresenter.class,
                VisualisationSettingsView.class,
                VisualisationSettingsViewImpl.class);
        bindPresenterWidget(VisualisationAssetsPresenter.class,
                VisualisationAssetsView.class,
                VisualisationAssetsViewImpl.class);
        bindPresenterWidget(VisualisationAssetsUploadFileDialogPresenter.class,
                VisualisationAssetsUploadFileDialogView.class,
                VisualisationAssetsUploadFileDialogViewImpl.class);
        bindPresenterWidget(VisualisationAssetsAddItemDialogPresenter.class,
                VisualisationAssetsAddFolderDialogView.class,
                VisualisationAssetsAddItemDialogViewImpl.class);
        bindPresenterWidget(VisualisationAssetsEditAssetDialogPresenter.class,
                VisualisationAssetsEditAssetDialogView.class,
                VisualisationAssetsEditAssetDialogViewImpl.class);
    }
}

