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
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// The explorer's chooser, the content of the dialog a document picker opens: a quick filter above
/// the explorer's tree ([ExplorerTree]), which its controller ([DocumentChooserController]) fills.
public class DocumentChooser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter nameFilter;
    @UiField
    ExplorerTree tree;
    @UiField
    DivElement status;

    private Consumer<String> onFilterChange = filter -> {
    };

    /// Makes an empty chooser.
    public DocumentChooser() {
        initWidget(BINDER.createAndBindUi(this));
        sinkEvents(Event.ONMOUSEUP | Event.FOCUSEVENTS);
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

    /// @param filter The filter's text, e.g. the one the chooser opens with.
    public void setFilter(final String filter) {
        nameFilter.setText(filter);
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

    /// @param moveDown What Down in the quick filter does, e.g. move to the chosen document.
    public void setOnFilterDown(final Runnable moveDown) {
        nameFilter.setMoveDown(moveDown);
    }

    /// Types into the quick filter, e.g. what was typed in the tree.
    ///
    /// @param text The text.
    public void typeInFilter(final String text) {
        nameFilter.type(text);
    }

    /// Tells screen reader users something, e.g. which document is chosen.
    ///
    /// @param message What to tell them.
    public void announce(final String message) {
        status.setInnerText(message);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentChooser> {

    }
}
