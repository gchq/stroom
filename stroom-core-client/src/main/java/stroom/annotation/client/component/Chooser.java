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

import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.quickfilter.client.QuickFilter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A chooser popup's content (BEM block `chooser`, `chooser.css`): a quick filter above a list of
/// values (a [DataGrid] with the list box profile, as high as its values up to the chooser's
/// height). Its controller ([ChooserController], or [MultiChooserController] for ticked values)
/// adds the list's columns, fills it and shows it.
///
/// @param <T> The type of the values.
public class Chooser<T> extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter nameFilter;
    @UiField
    DataGrid<T> list;

    private Consumer<String> onFilterChange = filter -> {
    };

    /// Makes an empty chooser.
    public Chooser() {
        initWidget(BINDER.createAndBindUi(this));
        list.setPageSize(DataGrid.MASSIVE_LIST_PAGE_SIZE);
        nameFilter.setOnChange(text -> onFilterChange.accept(text));
    }

    /// @return The list of values, for the controller to add its columns and selection to.
    public DataGrid<T> getList() {
        return list;
    }

    /// Shows the values, in place of those shown.
    ///
    /// @param values The values.
    public void setValues(final List<T> values) {
        list.setRowData(0, values);
        list.setRowCount(values.size());
    }

    /// Names the list of values for assistive technology, e.g. "Statuses" (a list must have one).
    ///
    /// @param name The list's name.
    public void setAccessibleName(final String name) {
        list.setAccessibleName(name);
    }

    /// Sets whether the values are ticked rather than chosen, which shows their tick boxes as
    /// pressable (the `chooser--multi` modifier).
    ///
    /// @param multiSelect Whether they are.
    public void setMultiSelect(final boolean multiSelect) {
        setStyleName("chooser--multi", multiSelect);
    }

    /// Sets the help the quick filter shows, e.g. how to write a filter.
    ///
    /// @param help Gives the help when it is asked for.
    public void setFilterHelp(final Supplier<SafeHtml> help) {
        nameFilter.registerPopupTextProvider(help);
    }

    /// Sets what is told when the filter's text changes.
    ///
    /// @param onFilterChange Given the text.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    /// Puts the focus on the list of values.
    public void focus() {
        list.setFocus(true);
    }


    // --------------------------------------------------------------------------------


    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<Widget, Chooser> {

    }
}
