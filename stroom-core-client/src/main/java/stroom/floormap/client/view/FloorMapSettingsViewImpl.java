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

package stroom.floormap.client.view;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.presenter.FloorMapSettingsPresenter.FloorMapSettingsView;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// GWT UiBinder view implementation for [stroom.floormap.client.presenter.FloorMapSettingsPresenter].
///
/// Contains [SimplePanel] containers for store reference pickers (events and facts),
/// a value format dropdown widget, and a schema grid with its associated toolbar.
///
/// The layout is defined in the companion UiBinder template
/// `FloorMapSettingsViewImpl.ui.xml`.
public class FloorMapSettingsViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements FloorMapSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    SimplePanel eventsStoreRefContainer;

    @UiField
    SimplePanel factsStoreRefContainer;

    @UiField
    SimplePanel valueFormatContainer;

    @UiField
    SimplePanel schemaToolbarContainer;

    @UiField
    TextArea histogramQuery;

    @UiField
    TextArea extentQuery;

    @UiField
    SimplePanel schemaGridContainer;

    @Inject
    public FloorMapSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // Every row on this tab holds an injected view rather than a control of
        // its own, so the FormGroup's visible <label> has no labelable element to
        // point `for` at and identity= in the ui.xml would associate nothing.
        // Naming the container as a group is what carries the label across.
        FloorMapAria.group(eventsStoreRefContainer, "Events Store");
        FloorMapAria.group(factsStoreRefContainer, "Facts Store");
        FloorMapAria.group(valueFormatContainer, "Value Format");
        FloorMapAria.group(schemaGridContainer, "Value Schema");
        FloorMapAria.group(schemaToolbarContainer, "Value Schema actions");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /// Sets the view for the events store reference picker.
    ///
    /// @param view the store reference picker view to place inside the events container
    @Override
    public void setEventsStoreRefView(final View view) {
        this.eventsStoreRefContainer.setWidget(view.asWidget());
    }

    /// Sets the view for the facts store reference picker.
    ///
    /// @param view the store reference picker view to place inside the facts container
    @Override
    public void setFactsStoreRefView(final View view) {
        this.factsStoreRefContainer.setWidget(view.asWidget());
    }

    /// Sets the widget used for selecting the value format (e.g. a dropdown).
    ///
    /// @param widget the value format selection widget
    @Override
    public void setValueFormatWidget(final Widget widget) {
        this.valueFormatContainer.setWidget(widget);
    }

    /// Sets the toolbar widget displayed above the schema grid.
    ///
    /// @param toolbar the toolbar widget for schema-related actions
    @Override
    public void setSchemaToolbar(final Widget toolbar) {
        this.schemaToolbarContainer.setWidget(toolbar);
    }

    /// Sets the grid widget that displays the schema configuration.
    ///
    /// @param grid the schema grid widget
    @Override
    public void setSchemaGrid(final Widget grid) {
        this.schemaGridContainer.setWidget(grid);
    }

    /// {@inheritDoc}
    ///
    /// Currently a no-op — this view does not yet adjust any UI elements
    /// in response to read-only state changes.
    ///
    /// @param readOnly `true` if the view should be read-only, `false` otherwise
    @Override
    public void onReadOnly(final boolean readOnly) {
        histogramQuery.setEnabled(!readOnly);
        extentQuery.setEnabled(!readOnly);
    }

    @Override
    public String getHistogramQuery() {
        return histogramQuery.getValue();
    }

    @Override
    public void setHistogramQuery(final String query) {
        histogramQuery.setValue(query);
    }

    @Override
    public String getExtentQuery() {
        return extentQuery.getValue();
    }

    @Override
    public void setExtentQuery(final String query) {
        extentQuery.setValue(query);
    }

    @SuppressWarnings("unused")
    @UiHandler("histogramQuery")
    public void onHistogramQuery(final ValueChangeEvent<String> event) {
        fireDirty();
    }

    @SuppressWarnings("unused")
    @UiHandler("extentQuery")
    public void onExtentQuery(final ValueChangeEvent<String> event) {
        fireDirty();
    }

    private void fireDirty() {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    // --------------------------------------------------------------------------------

    /// GWT UiBinder interface that binds `FloorMapSettingsViewImpl.ui.xml`
    /// to this view implementation.
    public interface Binder extends UiBinder<Widget, FloorMapSettingsViewImpl> {

    }
}
