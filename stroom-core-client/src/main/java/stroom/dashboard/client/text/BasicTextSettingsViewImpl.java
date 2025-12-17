/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client.text;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.text.BasicTextSettingsPresenter.BasicTextSettingsView;
import stroom.docref.HasDisplayValue;
import stroom.item.client.SelectionBox;
import stroom.query.api.ColumnRef;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BasicTextSettingsViewImpl extends ViewWithUiHandlers<BasicTextSettingsUiHandlers>
        implements BasicTextSettingsView {

    private static final HasDisplayValue ANY = () -> "Any";
    private static final HasDisplayValue NONE = () -> "";

    private final Widget widget;

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<HasDisplayValue> table;
    @UiField
    SelectionBox<HasDisplayValue> streamIdColumn;
    @UiField
    SelectionBox<HasDisplayValue> partNoColumn;
    @UiField
    SelectionBox<HasDisplayValue> recordNoColumn;
    @UiField
    SelectionBox<HasDisplayValue> lineFromColumn;
    @UiField
    SelectionBox<HasDisplayValue> colFromColumn;
    @UiField
    SelectionBox<HasDisplayValue> lineToColumn;
    @UiField
    SelectionBox<HasDisplayValue> colToColumn;
    @UiField
    SimplePanel pipeline;
    @UiField
    CustomCheckBox showAsHtml;
    @UiField
    CustomCheckBox showStepping;

    @Inject
    public BasicTextSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void setId(final String id) {
        this.id.setText(id);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setTableList(final List<Component> tableList) {
        final HasDisplayValue table = this.table.getValue();

        this.table.clear();
        this.table.addItem(ANY);

        final List<HasDisplayValue> newList = tableList.stream()
                .map(e ->
                        (HasDisplayValue) e)
                .collect(Collectors.toList());
        this.table.addItems(newList);

        // Reselect table id.
        this.table.setValue(table);
    }

    @Override
    public Component getTable() {
        if (ANY.equals(this.table.getValue())) {
            return null;
        }
        return (Component) this.table.getValue();
    }

    @Override
    public void setTable(final Component table) {
        if (table == null) {
            this.table.setValue(ANY);
        } else {
            this.table.setValue(table);
        }
    }

    @Override
    public void setColumns(final List<ColumnRef> columns) {
        setFieldNames(columns, streamIdColumn);
        setFieldNames(columns, partNoColumn);
        setFieldNames(columns, recordNoColumn);
        setFieldNames(columns, lineFromColumn);
        setFieldNames(columns, colFromColumn);
        setFieldNames(columns, lineToColumn);
        setFieldNames(columns, colToColumn);
    }

    private void setFieldNames(final List<ColumnRef> columns, final SelectionBox<HasDisplayValue> ctrl) {
        final HasDisplayValue selected = ctrl.getValue();
        ctrl.clear();
        ctrl.addItem(NONE);
        final List<HasDisplayValue> newList = columns.stream().map(e -> (HasDisplayValue) e)
                .sorted(Comparator.comparing(HasDisplayValue::getDisplayValue))
                .collect(Collectors.toList());
        ctrl.addItems(newList);
        ctrl.setValue(selected);
    }

    @Override
    public ColumnRef getStreamIdColumn() {
        return getColumn(streamIdColumn);
    }

    @Override
    public void setStreamIdColumn(final ColumnRef column) {
        streamIdColumn.setValue(column);
    }

    @Override
    public ColumnRef getPartNoColumn() {
        return getColumn(partNoColumn);
    }

    @Override
    public void setPartNoColumn(final ColumnRef column) {
        partNoColumn.setValue(column);
    }

    @Override
    public ColumnRef getRecordNoColumn() {
        return getColumn(recordNoColumn);
    }

    @Override
    public void setRecordNoColumn(final ColumnRef column) {
        recordNoColumn.setValue(column);
    }

    @Override
    public ColumnRef getLineFromColumn() {
        return getColumn(lineFromColumn);
    }

    @Override
    public void setLineFromColumn(final ColumnRef column) {
        lineFromColumn.setValue(column);
    }

    @Override
    public ColumnRef getColFromColumn() {
        return getColumn(colFromColumn);
    }

    @Override
    public void setColFromColumn(final ColumnRef column) {
        colFromColumn.setValue(column);
    }

    @Override
    public ColumnRef getLineToColumn() {
        return getColumn(lineToColumn);
    }

    @Override
    public void setLineToColumn(final ColumnRef column) {
        lineToColumn.setValue(column);
    }

    @Override
    public ColumnRef getColToColumn() {
        return getColumn(colToColumn);
    }

    @Override
    public void setColToColumn(final ColumnRef column) {
        colToColumn.setValue(column);
    }

    private ColumnRef getColumn(final SelectionBox<HasDisplayValue> ctrl) {
        if (ctrl.getValue() == null || NONE.equals(ctrl.getValue())) {
            return null;
        }
        return (ColumnRef) ctrl.getValue();
    }

    @Override
    public void setPipelineView(final View view) {
        final Widget w = view.asWidget();
        w.getElement().getStyle().setWidth(100, Unit.PCT);
        w.getElement().getStyle().setMargin(0, Unit.PX);
        pipeline.setWidget(w);
    }

    @Override
    public boolean isShowAsHtml() {
        return this.showAsHtml.getValue();
    }

    @Override
    public void setShowAsHtml(final boolean showAsHtml) {
        this.showAsHtml.setValue(showAsHtml);
    }

    @Override
    public boolean isShowStepping() {
        return this.showStepping.getValue();
    }

    @Override
    public void setShowStepping(final boolean showStepping) {
        this.showStepping.setValue(showStepping);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    @UiHandler("table")
    public void onTableValueChange(final ValueChangeEvent<HasDisplayValue> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTableChange();
        }
    }

    public interface Binder extends UiBinder<Widget, BasicTextSettingsViewImpl> {

    }
}
