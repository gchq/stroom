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

package stroom.task.client.component;

import stroom.util.shared.NullSafe;
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
import java.util.function.Supplier;

/// The Server Tasks screen: the tasks running on every node, with a filter above them.
public class ServerTasks extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter nameFilter;
    @UiField
    TaskManagerList list;

    private Consumer<String> onFilterChange = text -> {
    };

    /// Makes the screen, with no content until it is given it.
    public ServerTasks() {
        initWidget(BINDER.createAndBindUi(this));
        nameFilter.setOnChange(text -> onFilterChange.accept(text));
    }

    /// @param onFilterChange Given the filter's text when the user changes it.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// Sets the help shown by the filter's syntax help button.
    ///
    /// @param popupTextSupplier Supplies the help.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    /// @return The tasks, which its controller attaches the list's controller to.
    public TaskManagerList getList() {
        return list;
    }

    /// Shows why the server rejected the filter, or clears it when passed null.
    ///
    /// @param filterError Why, or null.
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter its recent filters.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    /// Puts the focus on the filter.
    public void focusFilter() {
        nameFilter.focus();
    }

    /// Puts text in the filter.
    ///
    /// @param nameFilter The text.
    public void setNameFilter(final String nameFilter) {
        this.nameFilter.setText(NullSafe.get(nameFilter, String::trim));
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, ServerTasks> {

    }
}
