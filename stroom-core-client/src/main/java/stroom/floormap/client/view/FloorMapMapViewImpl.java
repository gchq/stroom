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

import stroom.floormap.client.presenter.FloorMapMapPresenter;
import stroom.floormap.client.presenter.FloorMapMapPresenter.FloorMapMapView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/// View implementation for the floor map canvas area.
///
/// Uses a UiBinder layout to host the map canvas in the main area,
/// the right-hand dock in a resizable east column beside it, and the timeline
/// control strip docked beneath both at a fixed, non-resizable height.
/// Slot routing directs content from
/// [FloorMapMapPresenter#MAP] into the map panel,
/// [FloorMapMapPresenter#DOCK] into the dock panel, and
/// [FloorMapMapPresenter#TIMELINE] into the timeline panel.
public class FloorMapMapViewImpl extends ViewImpl implements FloorMapMapView {

    private final Widget widget;

    @UiField
    ThinSplitLayoutPanel topSplitPanel;
    @UiField
    SimplePanel mapPanel;
    @UiField
    SimplePanel dockPanel;
    @UiField
    SimplePanel timelinePanel;

    @Inject
    public FloorMapMapViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /// Routes GWTP slot content into the map, dock, or timeline panel.
    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (FloorMapMapPresenter.MAP.equals(slot)) {
            mapPanel.setWidget(content);
        } else if (FloorMapMapPresenter.DOCK.equals(slot)) {
            dockPanel.setWidget(content);
        } else if (FloorMapMapPresenter.TIMELINE.equals(slot)) {
            timelinePanel.setWidget(content);
        }
    }

    @Override
    public void setDockVisible(final boolean visible) {
        // Hides the dock and its splitter while retaining the dragged width.
        topSplitPanel.setWidgetHidden(dockPanel, !visible);
    }

    public interface Binder extends UiBinder<Widget, FloorMapMapViewImpl> {

    }
}
