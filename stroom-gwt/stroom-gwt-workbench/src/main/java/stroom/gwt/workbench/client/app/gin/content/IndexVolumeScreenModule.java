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


package stroom.gwt.workbench.client.app.gin.content;

import stroom.index.client.presenter.IndexVolumeEditPresenter;
import stroom.index.client.presenter.IndexVolumeGroupEditPresenter;
import stroom.index.client.presenter.IndexVolumeGroupEditPresenter.IndexVolumeGroupEditView;
import stroom.index.client.presenter.IndexVolumeGroupPresenter;
import stroom.index.client.view.IndexVolumeEditViewImpl;
import stroom.index.client.view.IndexVolumeGroupEditViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The index volume bindings of Stroom's `IndexModule` (`stroom/index/client/gin/IndexModule.java`):
/// the 'Index Volumes' tab and its group and volume dialogs, without the index editor's graph.
public class IndexVolumeScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(IndexVolumeGroupPresenter.class);
        bindPresenterWidget(IndexVolumeGroupEditPresenter.class,
                IndexVolumeGroupEditView.class,
                IndexVolumeGroupEditViewImpl.class);
        bindPresenterWidget(IndexVolumeEditPresenter.class,
                IndexVolumeEditPresenter.IndexVolumeEditView.class,
                IndexVolumeEditViewImpl.class);
    }
}
