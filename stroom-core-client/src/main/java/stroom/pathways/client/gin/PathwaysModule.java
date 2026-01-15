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

package stroom.pathways.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.pathways.client.PathwaysPlugin;
import stroom.pathways.client.TracesPlugin;
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
import stroom.pathways.client.presenter.TracesPresenter;
import stroom.pathways.client.presenter.TracesPresenter.TracesView;
import stroom.pathways.client.view.ConstraintEditViewImpl;
import stroom.pathways.client.view.PathwayEditViewImpl;
import stroom.pathways.client.view.PathwayTreeViewImpl;
import stroom.pathways.client.view.PathwaysSettingsViewImpl;
import stroom.pathways.client.view.PathwaysSplitViewImpl;
import stroom.pathways.client.view.TracesViewImpl;

public class PathwaysModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(PathwaysPlugin.class);
        bindPlugin(TracesPlugin.class);
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
        bindPresenterWidget(TracesPresenter.class,
                TracesView.class,
                TracesViewImpl.class);
        bindPresenterWidget(ConstraintEditPresenter.class,
                ConstraintEditView.class,
                ConstraintEditViewImpl.class);
    }
}
