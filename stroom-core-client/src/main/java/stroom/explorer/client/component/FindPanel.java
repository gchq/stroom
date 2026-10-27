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

package stroom.explorer.client.component;

import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.explorer.client.presenter.FindView;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A filter above a list of what it finds, its owner's: the content of Find, Recent Items and the
/// annotation finders. The annotation presenters not yet split see it as a [FindView].
public class FindPanel extends Component implements FindView {

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

    @Override
    public void setUiHandlers(final FindUiHandlers uiHandlers) {
        setOnFilterChange(uiHandlers::changeQuickFilter);
        setOnFilterKeyDown(uiHandlers::onFilterKeyDown);
    }

    @Override
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    @Override
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    @Override
    public void setResultView(final IsWidget view) {
        bottomPanel.setWidget(view.asWidget());
    }

    @Override
    public void focus() {
        nameFilter.forceFocus();
    }

    @Override
    public void setDialogMode(final boolean dialog) {
        panel.setStyleName(DIALOG, dialog);
    }

    @Override
    public void setFilterGuidance(final String accessibleName, final String description) {
        nameFilter.setAccessibleName(accessibleName);
        nameFilter.setDescription(description);
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        onFilterChange.accept(nameFilter.getText());
    }

    @UiHandler("nameFilter")
    void onFilterKeyDown(final KeyDownEvent event) {
        onFilterKeyDown.accept(event);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FindPanel> {

    }
}
