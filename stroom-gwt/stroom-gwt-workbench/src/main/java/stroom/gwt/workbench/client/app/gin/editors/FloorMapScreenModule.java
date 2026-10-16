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

import stroom.document.asset.client.presenter.DocumentAssetChooserPresenter;
import stroom.document.asset.client.presenter.DocumentAssetChooserPresenter.DocumentAssetChooserView;
import stroom.document.asset.client.view.DocumentAssetChooserViewImpl;
import stroom.floormap.client.presenter.FloorMapCanvasPresenter;
import stroom.floormap.client.presenter.FloorMapCanvasPresenter.FloorMapCanvasView;
import stroom.floormap.client.presenter.FloorMapDockPresenter;
import stroom.floormap.client.presenter.FloorMapDockPresenter.FloorMapDockView;
import stroom.floormap.client.presenter.FloorMapEditorPresenter;
import stroom.floormap.client.presenter.FloorMapEditorPresenter.FloorMapEditorView;
import stroom.floormap.client.presenter.FloorMapFactListPresenter;
import stroom.floormap.client.presenter.FloorMapFactListPresenter.FloorMapFactListView;
import stroom.floormap.client.presenter.FloorMapLayerStylePresenter;
import stroom.floormap.client.presenter.FloorMapLayerStylePresenter.FloorMapLayerStyleView;
import stroom.floormap.client.presenter.FloorMapLayersPresenter;
import stroom.floormap.client.presenter.FloorMapLayersPresenter.FloorMapLayersView;
import stroom.floormap.client.presenter.FloorMapObjectEditPresenter;
import stroom.floormap.client.presenter.FloorMapObjectEditPresenter.FloorMapObjectEditView;
import stroom.floormap.client.presenter.FloorMapSetScalePresenter;
import stroom.floormap.client.presenter.FloorMapSetScalePresenter.FloorMapSetScaleView;
import stroom.floormap.client.presenter.FloorMapTimeListPresenter;
import stroom.floormap.client.presenter.FloorMapTimeListPresenter.FloorMapTimeListView;
import stroom.floormap.client.presenter.FloorMapTimelinePresenter;
import stroom.floormap.client.presenter.FloorMapTimelinePresenter.FloorMapTimelineView;
import stroom.floormap.client.presenter.FloorMapTimelineSettingsPresenter;
import stroom.floormap.client.presenter.FloorMapTimelineSettingsPresenter.FloorMapTimelineSettingsView;
import stroom.floormap.client.view.FloorMapCanvasViewImpl;
import stroom.floormap.client.view.FloorMapDockViewImpl;
import stroom.floormap.client.view.FloorMapEditorViewImpl;
import stroom.floormap.client.view.FloorMapFactListViewImpl;
import stroom.floormap.client.view.FloorMapLayerStyleViewImpl;
import stroom.floormap.client.view.FloorMapLayersViewImpl;
import stroom.floormap.client.view.FloorMapObjectEditViewImpl;
import stroom.floormap.client.view.FloorMapSetScaleViewImpl;
import stroom.floormap.client.view.FloorMapTimeListViewImpl;
import stroom.floormap.client.view.FloorMapTimelineSettingsViewImpl;
import stroom.floormap.client.view.FloorMapTimelineViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `FloorMapModule`
/// (`stroom/floormap/client/gin/FloorMapModule.java`) that a floor map's Editor tab
/// ([FloorMapEditorPresenter]) needs, and the document asset chooser its asset boxes open
/// (`stroom/document/asset/client/gin/DocumentAssetModule.java`), without their plugins and app
/// services.
public class FloorMapScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(FloorMapEditorPresenter.class,
                FloorMapEditorView.class,
                FloorMapEditorViewImpl.class);
        bindPresenterWidget(FloorMapCanvasPresenter.class,
                FloorMapCanvasView.class,
                FloorMapCanvasViewImpl.class);
        bindPresenterWidget(FloorMapTimelinePresenter.class,
                FloorMapTimelineView.class,
                FloorMapTimelineViewImpl.class);
        bindPresenterWidget(FloorMapTimelineSettingsPresenter.class,
                FloorMapTimelineSettingsView.class,
                FloorMapTimelineSettingsViewImpl.class);
        bindPresenterWidget(FloorMapFactListPresenter.class,
                FloorMapFactListView.class,
                FloorMapFactListViewImpl.class);
        bindPresenterWidget(FloorMapTimeListPresenter.class,
                FloorMapTimeListView.class,
                FloorMapTimeListViewImpl.class);
        bindPresenterWidget(FloorMapObjectEditPresenter.class,
                FloorMapObjectEditView.class,
                FloorMapObjectEditViewImpl.class);
        bindPresenterWidget(FloorMapDockPresenter.class,
                FloorMapDockView.class,
                FloorMapDockViewImpl.class);
        bindPresenterWidget(FloorMapLayersPresenter.class,
                FloorMapLayersView.class,
                FloorMapLayersViewImpl.class);
        bindPresenterWidget(FloorMapLayerStylePresenter.class,
                FloorMapLayerStyleView.class,
                FloorMapLayerStyleViewImpl.class);
        bindPresenterWidget(FloorMapSetScalePresenter.class,
                FloorMapSetScaleView.class,
                FloorMapSetScaleViewImpl.class);
        bindPresenterWidget(DocumentAssetChooserPresenter.class,
                DocumentAssetChooserView.class,
                DocumentAssetChooserViewImpl.class);
    }
}
