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

import stroom.floormap.client.component.FloorMapEditorController;
import stroom.floormap.client.component.FloorMapMapController;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `FloorMapModule`
/// (`stroom/floormap/client/gin/FloorMapModule.java`) that a floor map's Editor tab
/// ([FloorMapEditorController]), Map tab ([FloorMapMapController], with its Tracking, Groups and
/// cluster member lists) need, and the dialog that initialises a new floor map
/// (`FloorMapInitController`), without their plugins and app services.
public class FloorMapScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
    }
}
