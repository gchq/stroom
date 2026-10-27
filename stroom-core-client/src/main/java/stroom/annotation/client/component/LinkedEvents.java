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

package stroom.annotation.client.component;

import stroom.annotation.shared.EventId;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// An annotation's Linked Events tab: its events, with Add and Remove, above the data of the one
/// selected (shown here by its controller).
public class LinkedEvents extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<EventId> dataGrid;
    @UiField
    SimplePanel data;

    private final ButtonView add;
    private final ButtonView remove;
    private Consumer<LinkedEventsAction> onAction = action -> {
    };

    /// Makes the tab, with no events.
    public LinkedEvents() {
        initWidget(BINDER.createAndBindUi(this));
        // On the pager's own toolbar, which a template can't add to
        add = pager.getToolbar().button(SvgImage.ADD, "Add Event Link",
                () -> onAction.accept(LinkedEventsAction.ADD));
        add.setEnabled(false);
        remove = pager.getToolbar().button(SvgImage.DELETE, "Remove Event Link",
                () -> onAction.accept(LinkedEventsAction.REMOVE));
        remove.setEnabled(false);
    }

    /// @return The grid of events, for the controller to give columns and rows.
    public DataGrid<EventId> getDataGrid() {
        return dataGrid;
    }

    /// @param data The data of the event selected.
    public void setData(final IsWidget data) {
        this.data.setWidget(data);
    }

    /// @param addEnabled    Whether an event can be linked.
    /// @param removeEnabled Whether the event selected can be unlinked.
    public void setActionsEnabled(final boolean addEnabled, final boolean removeEnabled) {
        add.setEnabled(addEnabled);
        remove.setEnabled(removeEnabled);
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<LinkedEventsAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, LinkedEvents> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the annotation's linked events.
    public enum LinkedEventsAction {
        /// Links an event.
        ADD,
        /// Unlinks the event selected.
        REMOVE
    }
}
