/*
 * Copyright 2016 Crown Copyright
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

package stroom.pathways.client.view;

import stroom.pathways.client.presenter.PathwayEditPresenter.PathwayEditView;
import stroom.svg.client.Preset;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tab.client.view.LinkTabBar;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MySplitLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class PathwayEditViewImpl extends ViewImpl implements PathwayEditView {

    private final Widget widget;
    private ButtonPanel buttonPanel;

    @UiField
    FlowPanel toolbarWidgets;
    @UiField
    MySplitLayoutPanel mainSplit;
    @UiField
    FlowPanel lowerPanel;
    @UiField
    MySplitLayoutPanel centreSplit;
    @UiField
    FormGroup treeGroup;
    @UiField
    SimplePanel tree;
    @UiField
    LinkTabBar nodeTabBar;
    @UiField
    LayerContainer nodeLayerContainer;
    @UiField
    Label nodePath;
    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    @Inject
    public PathwayEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public ButtonView addButton(final Preset preset) {
        if (buttonPanel == null) {
            buttonPanel = new ButtonPanel();
            toolbarWidgets.add(buttonPanel);
        }
        return buttonPanel.addButton(preset);
    }

    @Override
    public void setLowerShare(final double share) {
        // Of the room above and below the splitter, which is the whole tab less its toolbar.
        final int height = mainSplit.getOffsetHeight();
        if (height <= 0) {
            // Nothing laid out to measure yet, so ask again rather than settling on zero — but only
            // while the tab is still on the screen, or a tab closed before it was laid out would ask
            // for ever.
            if (widget.isAttached()) {
                Scheduler.get().scheduleDeferred(() -> setLowerShare(share));
            }
            return;
        }
        mainSplit.setWidgetSize(lowerPanel, height * share);
    }

    @Override
    public void setTreeShare(final double share, final Runnable onSized) {
        // Of the room these two share, not of the window — the explorer down the side of the screen
        // takes a good part of the window and none of it is this.
        final int width = centreSplit.getOffsetWidth();
        if (width <= 0) {
            // Nothing laid out to measure yet. Splitting nothing would leave the drawing with no room
            // at all, so this waits and asks again rather than settling on zero — but only while the
            // tab is still on the screen, or a tab closed before it was laid out would ask for ever.
            if (widget.isAttached()) {
                Scheduler.get().scheduleDeferred(() -> setTreeShare(share, onSized));
            }
            return;
        }
        centreSplit.setWidgetSize(treeGroup, width * share);
        // Told afterwards rather than alongside, because whatever is drawn in there is placed against
        // a width that has only just stopped moving.
        onSized.run();
    }

    @Override
    public void setTree(final View view) {
        tree.setWidget(view.asWidget());
    }

    @Override
    public LinkTabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    @Override
    public void setNodePath(final String path) {
        nodePath.setText(NullSafe.string(path));
    }

    @Override
    public LinkTabBar getNodeTabBar() {
        return nodeTabBar;
    }

    @Override
    public LayerContainer getNodeLayerContainer() {
        return nodeLayerContainer;
    }

    public interface Binder extends UiBinder<Widget, PathwayEditViewImpl> {

    }
}
