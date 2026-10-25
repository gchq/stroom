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

import stroom.annotation.client.ChangeAssignedToPresenter;
import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.annotation.client.ChangeAssignedToViewImpl;
import stroom.annotation.client.ChangeStatusPresenter;
import stroom.annotation.client.ChangeStatusPresenter.ChangeStatusView;
import stroom.annotation.client.ChangeStatusViewImpl;
import stroom.index.client.presenter.DenseVectorFieldPresenter;
import stroom.index.client.presenter.DenseVectorFieldPresenter.DenseVectorFieldView;
import stroom.index.client.presenter.IndexDataPresenter;
import stroom.index.client.presenter.IndexDataPresenter.IndexDataView;
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
import stroom.index.client.view.IndexDataViewImpl;
import stroom.index.client.view.IndexFieldEditViewImpl;
import stroom.index.client.view.IndexSettingsViewImpl;
import stroom.index.client.view.IndexVolumeEditViewImpl;
import stroom.index.client.view.IndexVolumeGroupEditViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `IndexModule`
/// (`stroom/index/client/gin/IndexModule.java`),
/// without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
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
        // The index's Data tab, and the annotation dialogs its results table opens
        bindPresenterWidget(IndexDataPresenter.class,
                IndexDataView.class,
                IndexDataViewImpl.class);
        bindPresenterWidget(ChangeStatusPresenter.class,
                ChangeStatusView.class,
                ChangeStatusViewImpl.class);
        bindPresenterWidget(ChangeAssignedToPresenter.class,
                ChangeAssignedToView.class,
                ChangeAssignedToViewImpl.class);
    }
}

