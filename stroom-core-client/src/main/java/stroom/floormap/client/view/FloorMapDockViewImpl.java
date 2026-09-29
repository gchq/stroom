/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.view;

import stroom.floormap.client.presenter.FloorMapDockPresenter.FloorMapDockView;
import stroom.widget.tab.client.presenter.TabBar;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.ViewImpl;

/// View for [stroom.floormap.client.presenter.FloorMapDockPresenter] — a
/// curve-tab bar over a layer container. Structurally the same as
/// `LinkTabPanelViewImpl`, kept as a dedicated pair to avoid re-binding the
/// shared `LinkTabPanelView` for a second presenter.
public class FloorMapDockViewImpl extends ViewImpl implements FloorMapDockView {

    private final Widget widget;

    @UiField
    TabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    @Inject
    public FloorMapDockViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    public interface Binder extends UiBinder<Widget, FloorMapDockViewImpl> {

    }
}
