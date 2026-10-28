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

import stroom.query.client.component.QueryButtons;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Severity;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A dashboard's query pane: the expression (shown here by its controller) below the expression's
/// actions, a button for the query's messages, shown when there are some, and Run.
public class QueryPane extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton addTerm;
    @UiField
    ToolbarButton addOperator;
    @UiField
    ToolbarButton copy;
    @UiField
    ToolbarButton disableItem;
    @UiField
    ToolbarButton deleteItem;
    @UiField
    ToolbarButton history;
    @UiField
    ToolbarButton favourites;
    @UiField
    ToolbarButton downloadQuery;
    @UiField
    ToolbarButton process;
    @UiField
    ToolbarButton messages;
    @UiField
    QueryButtons queryButtons;
    @UiField
    FlowPanel expressionTree;

    private Consumer<QueryPaneAction> onAction = action -> {
    };

    /// Makes the pane, with no expression.
    public QueryPane() {
        initWidget(BINDER.createAndBindUi(this));
        addTerm.setOnClick(() -> onAction.accept(QueryPaneAction.ADD_TERM));
        addOperator.setOnClick(() -> onAction.accept(QueryPaneAction.ADD_OPERATOR));
        copy.setOnClick(() -> onAction.accept(QueryPaneAction.COPY));
        disableItem.setOnClick(() -> onAction.accept(QueryPaneAction.DISABLE));
        deleteItem.setOnClick(() -> onAction.accept(QueryPaneAction.DELETE));
        history.setOnClick(() -> onAction.accept(QueryPaneAction.HISTORY));
        favourites.setOnClick(() -> onAction.accept(QueryPaneAction.FAVOURITES));
        downloadQuery.setOnClick(() -> onAction.accept(QueryPaneAction.DOWNLOAD));
        process.setOnClick(() -> onAction.accept(QueryPaneAction.PROCESS));
        messages.setOnClick(() -> onAction.accept(QueryPaneAction.SHOW_MESSAGES));
        queryButtons.setOnStart(() -> onAction.accept(QueryPaneAction.RUN));
    }

    /// @param expressionTree The expression.
    public void setExpressionTree(final IsWidget expressionTree) {
        this.expressionTree.add(expressionTree);
    }

    /// Enables the actions on the expression item selected, or disables them when none is.
    ///
    /// @param enabled     Whether an item is selected.
    /// @param disableName The disable button's name, as it enables an item that is disabled.
    public void setItemActionsEnabled(final boolean enabled, final String disableName) {
        copy.setEnabled(enabled);
        disableItem.setEnabled(enabled);
        // The title is the button's name, so it stays when the button is disabled
        disableItem.setTitle(disableName);
        deleteItem.setEnabled(enabled);
    }

    /// @param enabled Whether the query can be downloaded, i.e. it has a data source.
    public void setDownloadEnabled(final boolean enabled) {
        downloadQuery.setEnabled(enabled);
        // The title is the button's name, so it stays, and says why the button is disabled
        downloadQuery.setTitle(enabled
                ? "Download Query"
                : "Download Query (choose a data source first)");
    }

    /// @param visible Whether the user may process the query with a pipeline.
    public void setProcessVisible(final boolean visible) {
        process.setVisible(visible);
    }

    /// Shows the messages button when the query has messages, as their most severe kind.
    ///
    /// @param shown    Whether there are messages.
    /// @param severity The most severe of them, if there are any.
    public void setMessages(final boolean shown, final Severity severity) {
        messages.asWidget().setStyleName("toolbar-button--invisible", !shown);
        if (shown) {
            if (Severity.FATAL_ERROR.equals(severity) || Severity.ERROR.equals(severity)) {
                messages.setSvg(SvgImage.ERROR);
                messages.setTitle("Show Errors");
            } else if (Severity.WARNING.equals(severity)) {
                messages.setSvg(SvgImage.ALERT);
                messages.setTitle("Show Warnings");
            } else if (Severity.INFO.equals(severity)) {
                messages.setSvg(SvgImage.INFO);
                messages.setTitle("Show Messages");
            }
        }
    }

    /// @param searching Whether the query is running, which Run shows.
    public void setSearching(final boolean searching) {
        queryButtons.setSearching(searching);
    }

    /// @param enabled Whether the query can be run, i.e. its data source has loaded.
    public void setEnabled(final boolean enabled) {
        queryButtons.setEnabled(enabled);
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<QueryPaneAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryPane> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the query pane.
    public enum QueryPaneAction {
        /// Adds a term to the expression.
        ADD_TERM,
        /// Adds an operator to the expression.
        ADD_OPERATOR,
        /// Duplicates the item selected.
        COPY,
        /// Disables (or enables) the item selected.
        DISABLE,
        /// Deletes the item selected.
        DELETE,
        /// Shows the query's history.
        HISTORY,
        /// Shows the favourite queries.
        FAVOURITES,
        /// Downloads the query.
        DOWNLOAD,
        /// Processes the query with a pipeline.
        PROCESS,
        /// Shows the query's messages.
        SHOW_MESSAGES,
        /// Runs (or stops) the query.
        RUN
    }
}
