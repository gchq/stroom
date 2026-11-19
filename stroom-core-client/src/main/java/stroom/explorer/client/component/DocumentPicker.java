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

package stroom.explorer.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// A folder or document picker in a form, e.g. the parent folder of a new document: a quick filter
/// above the explorer's tree ([ExplorerTree]), which its controller ([DocumentPickerController]),
/// attached to it, fills.
public class DocumentPicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter nameFilter;
    @UiField
    ExplorerTree tree;

    private Consumer<String> onFilterChange = filter -> {
    };

    /// Makes an empty picker.
    public DocumentPicker() {
        initWidget(BINDER.createAndBindUi(this));
        nameFilter.setOnChange(text -> onFilterChange.accept(text));
    }

    /// @return The tree, for the controller to attach the tree's controller to.
    public ExplorerTree getTree() {
        return tree;
    }

    /// Puts the focus on the quick filter.
    public void focus() {
        nameFilter.forceFocus();
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    /// Shows why the server rejected the filter, or clears it.
    ///
    /// @param filterError Why, or null for no problem.
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    /// @param help Gives the help the quick filter shows, e.g. how to write a filter.
    public void setFilterHelp(final Supplier<SafeHtml> help) {
        nameFilter.registerPopupTextProvider(help);
    }

    /// @param contextHandler What the quick filter's context menu offers.
    public void setFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentPicker> {

    }
}
