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

package stroom.query.client.component;

import stroom.dashboard.client.component.VisFrame;
import stroom.dashboard.client.component.VisFrameLayout;
import stroom.dashboard.client.vis.VisButtonSpec;
import stroom.dashboard.client.vis.VisToolbarRow;
import stroom.data.pager.client.RefreshButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.MessagePanel;
import stroom.widget.tab.client.GlobalResizeObserver;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.BiConsumer;

/// A query's results as a visualisation: its toolbar (the visualisation's buttons, pause and
/// resume), then the space the visualisation's frame ([VisFrame], on the page's root) is laid over,
/// and a message while the visualisation loads or fails. The frame follows the space as it moves or
/// resizes, and hides while this is off the page.
public class QueryResultVis extends Component implements RequiresResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    VisToolbarRow toolbarRow;
    @UiField
    SimplePanel visContainer;
    @UiField
    MessagePanel messagePanel;

    private VisFrame visFrame;

    /// Makes it, with no frame until [#setVisFrame].
    public QueryResultVis() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        if (visFrame != null) {
            visFrame.asWidget().setVisible(true);
        }
        GlobalResizeObserver.addListener(getElement(), element -> {
            if (getOffsetWidth() > 0 && getOffsetHeight() > 0) {
                onResize();
            }
        });
    }

    @Override
    protected void onUnload() {
        GlobalResizeObserver.removeListener(getElement());
        super.onUnload();
        if (visFrame != null) {
            visFrame.asWidget().setVisible(false);
        }
    }

    /// @return The button that pauses and resumes the visualisation's updates.
    public RefreshButton getRefreshButton() {
        return toolbarRow.getRefreshButton();
    }

    /// Shows the buttons the visualisation asks for in its toolbar, replacing any it showed.
    ///
    /// @param buttons The buttons, none for no visualisation buttons.
    public void setVisButtons(final List<VisButtonSpec> buttons) {
        toolbarRow.setVisButtons(buttons);
    }

    /// @param onPress Given a toolbar button's id when it is pressed, and a toggle's new state (null
    ///                for a button that isn't a toggle).
    public void setOnVisButtonPress(final BiConsumer<String, Boolean> onPress) {
        toolbarRow.setOnPress(onPress);
    }

    /// @param visFrame The visualisation's frame, laid over this one's space.
    public void setVisFrame(final VisFrame visFrame) {
        this.visFrame = visFrame;
        onResize();
    }

    /// Lays the frame over this one's space.
    @Override
    public void onResize() {
        if (visFrame != null) {
            VisFrameLayout.layOver(visFrame, visContainer.getElement());
        }
    }

    /// @param msg The message to show over the visualisation, e.g. while it loads.
    public void showMessage(final String msg) {
        messagePanel.showMessage(msg);
    }

    /// Hides the message.
    public void hideMessage() {
        messagePanel.hideMessage();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryResultVis> {

    }
}
