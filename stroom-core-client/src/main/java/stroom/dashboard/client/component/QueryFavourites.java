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

package stroom.dashboard.client.component;

import stroom.dashboard.client.query.QueryCell;
import stroom.dashboard.shared.StoredQuery;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A query's favourites, above the expression of the one chosen, with buttons to add the current
/// query as a favourite and to rename or delete the one chosen.
public class QueryFavourites extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton createButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<StoredQuery> list;
    @UiField
    SimplePanel expression;

    private final MultiSelectionModelImpl<StoredQuery> selectionModel = new MultiSelectionModelImpl<>();
    private Consumer<FavouriteAction> onAction = action -> {
    };
    private Consumer<StoredQuery> onSelect = query -> {
    };
    private Runnable onChoose = () -> {
    };

    /// Makes the list, empty, with Change Favourite Name and Delete Favourite disabled.
    public QueryFavourites() {
        initWidget(BINDER.createAndBindUi(this));
        createButton.setOnClick(this::onCreateButton);
        editButton.setOnClick(this::onEditButton);
        deleteButton.setOnClick(this::onDeleteButton);
        // The one with the focus is chosen (and shown below it); Enter, or a double click, on one
        // opens it
        list.setSelectionModel(selectionModel,
                new DataGridSelectionEventManager<>(list, selectionModel, false));
        selectionModel.addSelectionChangeHandler(event -> onSelect.accept(selectionModel.getSelected()));
        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                onChoose.run();
            }
        });
    }

    /// Adds the list's column: each query's name, time and expression.
    ///
    /// @param dateTimeFormatter Formats the times.
    public void addColumn(final DateTimeFormatter dateTimeFormatter) {
        list.addColumn(DataGridUtil.columnBuilder((StoredQuery query) -> query,
                        () -> new QueryCell(dateTimeFormatter))
                .build(), "", 200);
    }

    /// Lists the favourites, none chosen.
    ///
    /// @param queries The favourites.
    public void setQueries(final List<StoredQuery> queries) {
        selectionModel.clear();
        list.setRowData(0, queries);
        list.setRowCount(queries.size(), true);
    }

    /// @return The favourite chosen, or `null` for none.
    public StoredQuery getSelected() {
        return selectionModel.getSelected();
    }

    /// @param expressionTree The chosen favourite's expression, a controller of its own.
    public void setExpressionTree(final IsWidget expressionTree) {
        expression.setWidget(expressionTree.asWidget());
    }

    /// @param enabled Whether Change Favourite Name and Delete Favourite are enabled, for a
    ///                favourite chosen.
    public void setSelectionActionsEnabled(final boolean enabled) {
        editButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    /// Puts the keyboard focus on Create Favourite From Current Query.
    public void focusCreate() {
        createButton.setFocus(true);
    }

    /// @param onAction Given the action of the button pressed.
    public void setOnAction(final Consumer<FavouriteAction> onAction) {
        this.onAction = onAction;
    }

    /// @param onSelect Given the favourite chosen, or `null` for none, each time the choice
    ///                 changes.
    public void setOnSelect(final Consumer<StoredQuery> onSelect) {
        this.onSelect = onSelect;
    }

    /// @param onChoose Called when Enter is pressed, or a double click made, on a favourite.
    public void setOnChoose(final Runnable onChoose) {
        this.onChoose = onChoose;
    }

    private void onCreateButton() {
        onAction.accept(FavouriteAction.CREATE);
    }

    private void onEditButton() {
        onAction.accept(FavouriteAction.EDIT);
    }

    private void onDeleteButton() {
        onAction.accept(FavouriteAction.DELETE);
    }


    // --------------------------------------------------------------------------------


    /// What the user asks of the favourites.
    public enum FavouriteAction {
        /// Make the current query a favourite.
        CREATE,
        /// Rename the favourite chosen.
        EDIT,
        /// Delete the favourite chosen.
        DELETE
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryFavourites> {

    }
}
