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

package stroom.floormap.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.floormap.client.FloorMapEventStorePlugin;
import stroom.floormap.client.FloorMapPlugin;
import stroom.floormap.client.presenter.FloorMapEditorPresenter;
import stroom.floormap.client.presenter.FloorMapEditorPresenter.FloorMapEditorView;
import stroom.floormap.client.presenter.FloorMapEventStorePresenter;
import stroom.floormap.client.presenter.FloorMapMapPresenter;
import stroom.floormap.client.presenter.FloorMapMapPresenter.FloorMapMapView;
import stroom.floormap.client.presenter.FloorMapPresenter;
import stroom.floormap.client.presenter.FloorMapQueryPresenter;
import stroom.floormap.client.presenter.FloorMapQueryPresenter.FloorMapQueryView;
import stroom.floormap.client.view.FloorMapEditorViewImpl;
import stroom.floormap.client.view.FloorMapMapViewImpl;
import stroom.floormap.client.view.FloorMapQueryViewImpl;

/// GIN module for the Floor Map feature.
/// Binds the presenters and views for the floor map components.
public class FloorMapModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(FloorMapPlugin.class);
        // The events store is a document of its own, creatable from the explorer, so it needs its
        // own plugin and editor rather than appearing inside the floor map's own editor.
        bindPlugin(FloorMapEventStorePlugin.class);

        bind(FloorMapPresenter.class);
        bind(FloorMapEventStorePresenter.class);

        // Lists a cluster's members, with a search box and filters over the
        // app-wide PagerPanel that holds the grid.

        bindPresenterWidget(FloorMapEditorPresenter.class,
                FloorMapEditorView.class,
                FloorMapEditorViewImpl.class);
        bindPresenterWidget(FloorMapMapPresenter.class,
                FloorMapMapView.class,
                FloorMapMapViewImpl.class);

        bindPresenterWidget(FloorMapQueryPresenter.class,
                FloorMapQueryView.class,
                FloorMapQueryViewImpl.class);

    }
}
