/*
 * Copyright 2023 Crown Copyright
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

import stroom.item.client.SelectionList;
import stroom.query.client.QueryHelpSelectionItem;
import stroom.query.shared.QueryHelpRow;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Supplier;

/// A query's help: its contents (functions, fields and the like) above the details of the one
/// selected, which can be inserted into the query or copied.
public class QueryHelp extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionList<QueryHelpRow, QueryHelpSelectionItem> selectionList;
    @UiField
    SimplePanel details;
    @UiField
    ToolbarButton copyButton;
    @UiField
    ToolbarButton insertButton;

    /// Makes empty help.
    public QueryHelp() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The contents' list, for its controller to give its model and follow.
    public SelectionList<QueryHelpRow, QueryHelpSelectionItem> getSelectionList() {
        return selectionList;
    }

    /// @param details The details of the item selected, or null for none.
    public void setDetails(final SafeHtml details) {
        final String htmlStr = details != null
                ? details.asString()
                : "";
        this.details.getElement().setInnerHTML(htmlStr);
    }

    /// @param enable Whether Insert and Copy can be pressed.
    public void setButtonsEnabled(final boolean enable) {
        copyButton.setEnabled(enable);
        insertButton.setEnabled(enable);
    }

    /// @param popupTextSupplier Gives the quick filter's tooltip.
    public void setQuickFilterTooltip(final Supplier<SafeHtml> popupTextSupplier) {
        selectionList.setPopupTextProvider(popupTextSupplier);
    }

    /// @param onCopy What to run when the user presses Copy.
    public void setOnCopy(final Runnable onCopy) {
        copyButton.setOnClick(onCopy);
    }

    /// @param onInsert What to run when the user presses Insert.
    public void setOnInsert(final Runnable onInsert) {
        insertButton.setOnClick(onInsert);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryHelp> {

    }
}
