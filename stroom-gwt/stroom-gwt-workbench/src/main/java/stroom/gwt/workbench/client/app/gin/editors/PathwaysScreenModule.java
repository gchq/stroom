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

import stroom.pathways.client.presenter.ConstraintEditPresenter;
import stroom.pathways.client.presenter.ConstraintEditPresenter.ConstraintEditView;
import stroom.pathways.client.presenter.PathwayEditPresenter;
import stroom.pathways.client.presenter.PathwayEditPresenter.PathwayEditView;
import stroom.pathways.client.presenter.PathwayTreePresenter;
import stroom.pathways.client.presenter.PathwayTreePresenter.PathwayTreeView;
import stroom.pathways.client.presenter.PathwaysPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.client.presenter.PathwaysSplitPresenter;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.client.view.ConstraintEditViewImpl;
import stroom.pathways.client.view.PathwayEditViewImpl;
import stroom.pathways.client.view.PathwayTreeViewImpl;
import stroom.pathways.client.view.PathwaysSettingsViewImpl;
import stroom.pathways.client.view.PathwaysSplitViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `PathwaysModule`
/// (`stroom/pathways/client/gin/PathwaysModule.java`), without its plugins, app services and the
/// Traces screen.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class PathwaysScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(PathwaysPresenter.class);
        bindPresenterWidget(PathwaysSettingsPresenter.class,
                PathwaysSettingsView.class,
                PathwaysSettingsViewImpl.class);
        bindPresenterWidget(PathwayEditPresenter.class,
                PathwayEditView.class,
                PathwayEditViewImpl.class);
        bindPresenterWidget(PathwaysSplitPresenter.class,
                PathwaysSplitView.class,
                PathwaysSplitViewImpl.class);
        bindPresenterWidget(PathwayTreePresenter.class,
                PathwayTreeView.class,
                PathwayTreeViewImpl.class);
        bindPresenterWidget(ConstraintEditPresenter.class,
                ConstraintEditView.class,
                ConstraintEditViewImpl.class);
    }
}

