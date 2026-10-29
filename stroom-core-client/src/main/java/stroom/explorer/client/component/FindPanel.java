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
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiChild;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A filter above a list of what it finds: the content of Find, Recent Items, Chat History and the
/// annotation finders. A template declares it around its list (`<ex:content>`, see
/// [#addContent(Widget)]).
public class FindPanel extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String DIALOG = "find-panel--dialog";

    @UiField
    FlowPanel panel;
    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel bottomPanel;

    private Consumer<String> onFilterChange = filter -> {
    };
    private Consumer<KeyDownEvent> onFilterKeyDown = event -> {
    };

    /// Makes an empty panel, as a dialog's content.
    public FindPanel() {
        initWidget(BINDER.createAndBindUi(this));
        nameFilter.setOnChange(text -> onFilterChange.accept(text));
        nameFilter.addKeyDownHandler(event -> onFilterKeyDown.accept(event));
    }

    /// @param onFilterChange Given the filter's text whenever it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// @param onFilterKeyDown Given each key pressed in the filter, e.g. to open what is chosen on
    ///                        Enter.
    public void setOnFilterKeyDown(final Consumer<KeyDownEvent> onFilterKeyDown) {
        this.onFilterKeyDown = Objects.requireNonNull(onFilterKeyDown, "onFilterKeyDown");
    }

    /// Shows why the server rejected the filter, or clears it when passed null. See
    /// `ResultPage.filterError`.
    ///
    /// @param filterError Why, or null.
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter its recent filters.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    /// Gives the panel its list of what is found, once, from a template's `<ex:content>` child.
    ///
    /// @param content The list of what is found.
    @UiChild(tagname = "content", limit = 1)
    public void addContent(final Widget content) {
        bottomPanel.setWidget(content);
    }

    /// Puts the focus on the filter.
    public void focus() {
        nameFilter.forceFocus();
    }

    /// @param dialog Whether it is a dialog's content, whose top then has no padding of its own.
    public void setDialogMode(final boolean dialog) {
        panel.setStyleName(DIALOG, dialog);
    }

    /// Says what the filter is for and what may be typed in it, for screen readers.
    ///
    /// @param accessibleName What the filter is for, e.g. `Find by name`.
    /// @param description    What may be typed, or null for nothing.
    public void setFilterGuidance(final String accessibleName, final String description) {
        nameFilter.setAccessibleName(accessibleName);
        nameFilter.setDescription(description);
    }

    /// Sets the help shown by the filter's syntax help button.
    ///
    /// @param popupTextSupplier Supplies the help.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FindPanel> {

    }
}
