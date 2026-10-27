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

package stroom.query.client.component;

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A store's or index's Data tab: a StroomQL query of its rows (Enter runs it) with Reset, Run, Stop
/// and Create Dashboard, any error in the query, and the results table (shown here by its
/// controller).
public class QueryDataPane extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox query;
    @UiField
    ToolbarButton reset;
    @UiField
    ToolbarButton run;
    @UiField
    ToolbarButton stop;
    @UiField
    ToolbarButton createDashboard;
    @UiField
    ScrollPanel errorContainer;
    @UiField
    Label errorLabel;
    @UiField
    SimplePanel tableContainer;

    private Consumer<QueryDataAction> onAction = action -> {
    };

    /// Makes the tab, with no query or table.
    public QueryDataPane() {
        initWidget(BINDER.createAndBindUi(this));
        reset.setOnClick(() -> onAction.accept(QueryDataAction.RESET));
        run.setOnClick(() -> onAction.accept(QueryDataAction.RUN));
        stop.setOnClick(() -> onAction.accept(QueryDataAction.STOP));
        createDashboard.setOnClick(() -> onAction.accept(QueryDataAction.CREATE_DASHBOARD));
        query.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onAction.accept(QueryDataAction.RUN);
            }
        });
    }

    /// @param queryText The query.
    public void setQuery(final String queryText) {
        query.setText(queryText);
    }

    /// @return The query, as typed.
    public String getQuery() {
        return query.getText();
    }

    /// @param table The results table.
    public void setTable(final IsWidget table) {
        tableContainer.setWidget(table);
    }

    /// Shows an error in the query, and tells screen readers the query is invalid.
    ///
    /// @param error The error, or blank to clear it.
    public void setError(final String error) {
        if (error != null && !error.trim().isEmpty()) {
            errorLabel.setText(error);
            errorContainer.setVisible(true);
            // Shown, and told to screen readers (aria-invalid)
            FieldValidity.setInvalid(query.getElement(), true);
        } else {
            clearError();
        }
    }

    /// Clears the error.
    public void clearError() {
        errorLabel.setText("");
        errorContainer.setVisible(false);
        FieldValidity.setInvalid(query.getElement(), false);
    }

    /// Selects part of the query, e.g. where an error is, putting the focus in it.
    ///
    /// @param pos    Where the part starts.
    /// @param length The part's length.
    public void selectQueryRange(final int pos, final int length) {
        query.setFocus(true);
        query.setSelectionRange(pos, length);
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<QueryDataAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryDataPane> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the query.
    public enum QueryDataAction {
        /// Puts the default query back.
        RESET,
        /// Runs the query.
        RUN,
        /// Stops the query.
        STOP,
        /// Makes a dashboard that runs the query.
        CREATE_DASHBOARD
    }
}
