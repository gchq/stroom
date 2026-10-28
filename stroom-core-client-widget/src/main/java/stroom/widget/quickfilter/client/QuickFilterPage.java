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

package stroom.widget.quickfilter.client;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.HelpHTML;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A quick filter above what it filters, e.g. a list, which a template declares inside it:
///
/// ```
/// <v:QuickFilterPage ui:field="page" accessibleName="Filter users">
///     <dg:PagerPanel ui:field="pager">...</dg:PagerPanel>
/// </v:QuickFilterPage>
/// ```
///
/// The filter has no label unless given one, and no help unless a `<form:HelpHTML>` is declared
/// inside it too.
public class QuickFilterPage extends Component implements HasWidgets {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup formGroup;
    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel data;

    private Consumer<String> onFilterChange = text -> {
    };

    /// Makes a page with nothing to filter yet.
    public QuickFilterPage() {
        initWidget(BINDER.createAndBindUi(this));
        formGroup.setLabel(null);
        quickFilter.addValueChangeHandler(event -> onFilterChange.accept(quickFilter.getText()));
    }

    /// @param label The filter's label, or `null` for none.
    public void setLabel(final String label) {
        formGroup.setLabel(label);
    }

    /// @param helpText The filter's help.
    public void setHelpText(final SafeHtml helpText) {
        formGroup.overrideHelpText(helpText);
    }

    /// @param popupTextSupplier Gives the filter's help popup its text, e.g. the fields it can use.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
    }

    /// Puts the focus in the filter.
    public void focus() {
        quickFilter.forceFocus();
    }

    /// @param content What the filter filters, e.g. a list.
    public void setContent(final IsWidget content) {
        data.setWidget(content);
    }

    /// Sets the filter's text; if it changes, the filter change callback is told.
    ///
    /// @param quickFilterText The text.
    public void setQuickFilterText(final String quickFilterText) {
        final String currVal = quickFilter.getText();
        quickFilter.setText(quickFilterText);
        if (!Objects.equals(currVal, quickFilterText)) {
            onFilterChange.accept(quickFilterText);
        }
    }

    /// Shows why the server rejected the filter. See `ResultPage.filterError`.
    ///
    /// @param filterError The reason, or `null` to clear it.
    public void setFilterError(final String filterError) {
        quickFilter.setFilterError(filterError);
    }

    /// Names the filter's text box for screen readers, by what it filters (e.g. "Filter users"), as
    /// nothing on screen names it.
    ///
    /// @param accessibleName The name.
    public void setAccessibleName(final String accessibleName) {
        quickFilter.setAccessibleName(accessibleName);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        quickFilter.setContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    /// Sets what the filter filters, or the filter's help if it is a [HelpHTML], as a template
    /// declares them.
    ///
    /// @param widget The content or the help.
    @Override
    public void add(final Widget widget) {
        if (widget instanceof HelpHTML) {
            formGroup.add(widget);
        } else {
            data.setWidget(widget);
        }
    }

    /// Removes the content.
    @Override
    public void clear() {
        data.clear();
    }

    /// @return The content, if any.
    @Override
    public Iterator<Widget> iterator() {
        return data.iterator();
    }

    /// @param widget The content.
    /// @return Whether it was the content, and is removed.
    @Override
    public boolean remove(final Widget widget) {
        return data.remove(widget);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QuickFilterPage> {

    }
}
