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

package stroom.importexport.client.component;

import stroom.explorer.client.component.ExplorerTree;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// The content of the Export dialog: a tree of the documents and folders to tick, below a filter
/// and a button that filters them by type.
public class ExportConfigDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter nameFilter;
    @UiField
    ToolbarToggleButton filter;
    @UiField
    ExplorerTree tree;

    private Consumer<String> onFilterChange = text -> {
    };
    private Runnable onFilterTypes = () -> {
    };

    /// Makes empty content.
    public ExportConfigDialog() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus in the filter.
    public void focus() {
        nameFilter.forceFocus();
    }

    /// @return The tree of items to tick, for its controller to attach to.
    public ExplorerTree getTree() {
        return tree;
    }

    /// Shows why the server rejected the filter. See `FetchExplorerNodeResult.filterError`.
    ///
    /// @param filterError The reason, or null to clear it.
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// @param onFilterTypes Run when Filter Document Types is pressed.
    public void setOnFilterTypes(final Runnable onFilterTypes) {
        this.onFilterTypes = Objects.requireNonNull(onFilterTypes, "onFilterTypes");
    }

    /// Shows whether the document types are filtered: Filter Document Types is pressed only then,
    /// not just when it has been clicked.
    ///
    /// @param filtered Whether they are.
    public void setTypesFiltered(final boolean filtered) {
        filter.setState(filtered);
    }

    /// @return Filter Document Types' element, which the type filter's popup is shown at.
    public Element getFilterTypesElement() {
        return filter.getElement();
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        onFilterChange.accept(nameFilter.getText());
    }

    @UiHandler("filter")
    void onFilterClick(final ClickEvent event) {
        onFilterTypes.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExportConfigDialog> {

    }
}
