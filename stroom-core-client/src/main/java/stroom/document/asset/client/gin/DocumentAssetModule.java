/*
 * Copyright 2025 Crown Copyright
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

package stroom.document.asset.client.gin;

import stroom.core.client.gin.PluginModule;
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

public class DocumentAssetModule extends PluginModule {

    @Override
    protected void configure() {
        bindPresenterWidget(DocumentAssetPresenter.class, DocumentAssetView.class,
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
