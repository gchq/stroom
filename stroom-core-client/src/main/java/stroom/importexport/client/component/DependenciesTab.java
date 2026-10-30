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

import stroom.widget.component.client.Component;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// The Dependencies screen: the documents' dependencies, with a filter above them.
public class DependenciesTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter quickFilter;
    @UiField
    DependencyList list;

    private Consumer<String> onFilterChange = text -> {
    };

    /// Makes the screen, with no dependencies.
    public DependenciesTab() {
        initWidget(BINDER.createAndBindUi(this));
        quickFilter.setOnChange(text -> onFilterChange.accept(text));
    }

    /// @param onFilterChange Given the filter's text when the user changes it, or when it is set.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// @return The dependencies, which its controller attaches the list's controller to.
    public DependencyList getList() {
        return list;
    }

    /// @param helpTooltipText The help shown by the filter's syntax help button.
    public void setHelpTooltipText(final SafeHtml helpTooltipText) {
        quickFilter.registerPopupTextProvider(() -> helpTooltipText);
    }

    /// Puts text in the filter, as if the user typed it.
    ///
    /// @param text The text.
    public void setQuickFilterText(final String text) {
        quickFilter.setText(text);
        onFilterChange.accept(text);
    }

    /// Shows why the server rejected the filter, or clears it when passed null.
    ///
    /// @param filterError Why, or null.
    public void setFilterError(final String filterError) {
        quickFilter.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter its recent filters.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        quickFilter.setContextHandler(contextHandler);
    }

    /// Puts the focus on the filter.
    public void focusFilter() {
        quickFilter.focus();
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, DependenciesTab> {

    }
}
