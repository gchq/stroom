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

import stroom.document.asset.client.presenter.DocumentAssetAddItemDialogPresenter;
import stroom.document.asset.client.presenter.DocumentAssetAddItemDialogPresenter.DocumentAssetAddItemDialogView;
import stroom.document.asset.client.presenter.DocumentAssetEditAssetDialogPresenter;
import stroom.document.asset.client.presenter.DocumentAssetEditAssetDialogPresenter.DocumentAssetEditAssetDialogView;
import stroom.document.asset.client.presenter.DocumentAssetPresenter;
import stroom.document.asset.client.presenter.DocumentAssetPresenter.DocumentAssetView;
import stroom.document.asset.client.presenter.DocumentAssetUploadFileDialogPresenter;
import stroom.document.asset.client.presenter.DocumentAssetUploadFileDialogPresenter.DocumentAssetUploadFileDialogView;
import stroom.document.asset.client.view.DocumentAssetAddItemDialogViewImpl;
import stroom.document.asset.client.view.DocumentAssetEditAssetDialogViewImpl;
import stroom.document.asset.client.view.DocumentAssetUploadFileDialogViewImpl;
import stroom.document.asset.client.view.DocumentAssetViewImpl;
import stroom.visualisation.client.presenter.VisualisationPresenter;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter.VisualisationSettingsView;
import stroom.visualisation.client.view.VisualisationSettingsViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `VisualisationModule`
/// (`stroom/visualisation/client/gin/VisualisationModule.java`) and of the document asset
/// presenters its 'Assets' tab uses (`stroom/document/asset/client/gin/DocumentAssetModule.java`),
/// without their plugins and app services.
/// Presenters that Stroom binds with a listener are bound here as presenter widgets without one: a
/// story shows one by registering it as the handler of its event, as the listener would.
public class VisualisationScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(VisualisationPresenter.class);
        bindPresenterWidget(VisualisationSettingsPresenter.class,
                VisualisationSettingsView.class,
                VisualisationSettingsViewImpl.class);
        bindPresenterWidget(DocumentAssetPresenter.class,
                DocumentAssetView.class,
                DocumentAssetViewImpl.class);
        bindPresenterWidget(DocumentAssetUploadFileDialogPresenter.class,
                DocumentAssetUploadFileDialogView.class,
                DocumentAssetUploadFileDialogViewImpl.class);
        bindPresenterWidget(DocumentAssetAddItemDialogPresenter.class,
                DocumentAssetAddItemDialogView.class,
                DocumentAssetAddItemDialogViewImpl.class);
        bindPresenterWidget(DocumentAssetEditAssetDialogPresenter.class,
                DocumentAssetEditAssetDialogView.class,
                DocumentAssetEditAssetDialogViewImpl.class);
    }
}

