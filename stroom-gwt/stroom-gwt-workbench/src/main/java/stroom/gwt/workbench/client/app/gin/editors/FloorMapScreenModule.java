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

import stroom.floormap.client.presenter.FloorMapEditorPresenter;
import stroom.floormap.client.presenter.FloorMapEditorPresenter.FloorMapEditorView;
import stroom.floormap.client.presenter.FloorMapMapPresenter;
import stroom.floormap.client.presenter.FloorMapMapPresenter.FloorMapMapView;
import stroom.floormap.client.presenter.FloorMapObjectEditPresenter;
import stroom.floormap.client.presenter.FloorMapObjectEditPresenter.FloorMapObjectEditView;
import stroom.floormap.client.presenter.FloorMapSettingsPresenter;
import stroom.floormap.client.presenter.FloorMapSettingsPresenter.FloorMapSettingsView;
import stroom.floormap.client.view.FloorMapEditorViewImpl;
import stroom.floormap.client.view.FloorMapMapViewImpl;
import stroom.floormap.client.view.FloorMapObjectEditViewImpl;
import stroom.floormap.client.view.FloorMapSettingsViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `FloorMapModule`
/// (`stroom/floormap/client/gin/FloorMapModule.java`) that a floor map's Editor tab
/// ([FloorMapEditorPresenter]), Map tab ([FloorMapMapPresenter], with its Tracking, Groups and
/// cluster member lists) and Settings tab ([FloorMapSettingsPresenter]) need, the dialog that
/// initialises a new floor map (`FloorMapInitController`), and the document
/// asset chooser its asset boxes open
/// (`stroom/document/asset/client/gin/DocumentAssetModule.java`), without their plugins and app
/// services.
public class FloorMapScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(FloorMapEditorPresenter.class,
                FloorMapEditorView.class,
                FloorMapEditorViewImpl.class);
        bindPresenterWidget(FloorMapObjectEditPresenter.class,
                FloorMapObjectEditView.class,
                FloorMapObjectEditViewImpl.class);
        bindPresenterWidget(FloorMapSettingsPresenter.class,
                FloorMapSettingsView.class,
                FloorMapSettingsViewImpl.class);
        bindPresenterWidget(FloorMapMapPresenter.class,
                FloorMapMapView.class,
                FloorMapMapViewImpl.class);
    }
}
