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

package stroom.widget.quickfilter.client;

import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A quick filter above what it filters, bordered, as a dialog's content, e.g. the user picker. A
/// template declares what it filters inside it:
///
/// ```
/// <v:QuickFilterDialog ui:field="dialog" accessibleName="Filter users and groups">
///     <dg:PagerPanel ui:field="pager">...</dg:PagerPanel>
/// </v:QuickFilterDialog>
/// ```
public class QuickFilterDialog extends Component implements HasWidgets {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel data;
    @UiField
    AriaPanel status;

    private Consumer<String> onFilterChange = text -> {
    };

    /// Makes a dialog's content with nothing to filter yet.
    public QuickFilterDialog() {
        initWidget(BINDER.createAndBindUi(this));
        quickFilter.addValueChangeHandler(this::onFilterChange);
    }

    /// @param popupTextSupplier Gives the filter's help popup its text, e.g. the fields it can use.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
    }

    /// Puts the focus in the filter.
    public void focus() {
        quickFilter.forceFocus();
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
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// Sets what Down in the filter does, e.g. moves the focus into the list below it.
    ///
    /// @param moveDown What to do.
    public void setFilterMoveDown(final Runnable moveDown) {
        quickFilter.setMoveDown(moveDown);
    }

    /// Types characters in the filter (moving the focus to it), e.g. those typed in the list.
    ///
    /// @param text The characters.
    public void typeInFilter(final String text) {
        quickFilter.type(text);
    }

    /// Says something to screen readers (a polite live region), e.g. the item now chosen.
    ///
    /// @param message What to say.
    public void announce(final String message) {
        status.getElement().setInnerText(message);
    }

    /// Sets what the filter filters, as a template declares it.
    ///
    /// @param widget The content.
    @Override
    public void add(final Widget widget) {
        data.setWidget(widget);
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

    private void onFilterChange(final ValueChangeEvent<String> event) {
        onFilterChange.accept(quickFilter.getText());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QuickFilterDialog> {

    }
}
