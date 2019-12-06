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

package stroom.dashboard.client.text;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.text.BasicTextSettingsPresenter.BasicTextSettingsView;
import stroom.dashboard.shared.Field;
import stroom.item.client.ItemListBox;
import stroom.util.shared.HasDisplayValue;
import stroom.widget.tickbox.client.view.TickBox;

import java.util.List;
import java.util.stream.Collectors;

public class BasicTextSettingsViewImpl extends ViewWithUiHandlers<BasicTextSettingsUiHandlers> implements BasicTextSettingsView {
    private static final HasDisplayValue ANY = () -> "Any";
    private static final HasDisplayValue NONE = () -> "";

    private final Widget widget;

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    ItemListBox<HasDisplayValue> table;
    @UiField
    ItemListBox<HasDisplayValue> streamIdField;
    @UiField
    ItemListBox<HasDisplayValue> partNoField;
    @UiField
    ItemListBox<HasDisplayValue> recordNoField;
    @UiField
    ItemListBox<HasDisplayValue> lineFromField;
    @UiField
    ItemListBox<HasDisplayValue> colFromField;
    @UiField
    ItemListBox<HasDisplayValue> lineToField;
    @UiField
    ItemListBox<HasDisplayValue> colToField;
    @UiField
    SimplePanel pipeline;
    @UiField
    TickBox showAsHtml;
    @UiField
    TickBox showStepping;

    @Inject
    public BasicTextSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        table.addSelectionHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onTableChange();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
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
        final HasDisplayValue table = this.table.getSelectedItem();

        this.table.clear();
        this.table.addItem(ANY);

        final List<HasDisplayValue> newList = tableList.stream().map(e -> (HasDisplayValue) e).collect(Collectors.toList());
        this.table.addItems(newList);

        // Reselect table id.
        this.table.setSelectedItem(table);
    }

    @Override
    public Component getTable() {
        if (ANY.equals(this.table.getSelectedItem())) {
            return null;
        }
        return (Component) this.table.getSelectedItem();
    }

    @Override
    public void setTable(final Component table) {
        if (table == null) {
            this.table.setSelectedItem(ANY);
        } else {
            this.table.setSelectedItem(table);
        }
    }

    @Override
    public void setFields(final List<Field> fields) {
        setFieldNames(fields, streamIdField);
        setFieldNames(fields, partNoField);
        setFieldNames(fields, recordNoField);
        setFieldNames(fields, lineFromField);
        setFieldNames(fields, colFromField);
        setFieldNames(fields, lineToField);
        setFieldNames(fields, colToField);
    }

    private void setFieldNames(final List<Field> fields, final ItemListBox<HasDisplayValue> ctrl) {
        final HasDisplayValue selected = ctrl.getSelectedItem();
        ctrl.clear();
        ctrl.addItem(NONE);
        final List<HasDisplayValue> newList = fields.stream().map(e -> (HasDisplayValue) e).collect(Collectors.toList());
        ctrl.addItems(newList);
        ctrl.setSelectedItem(selected);
    }

    @Override
    public Field getStreamIdField() {
        return getField(streamIdField);
    }

    @Override
    public void setStreamIdField(final Field field) {
        streamIdField.setSelectedItem(field);
    }

    @Override
    public Field getPartNoField() {
        return getField(partNoField);
    }

    @Override
    public void setPartNoField(final Field field) {
        partNoField.setSelectedItem(field);
    }

    @Override
    public Field getRecordNoField() {
        return getField(recordNoField);
    }

    @Override
    public void setRecordNoField(final Field field) {
        recordNoField.setSelectedItem(field);
    }

    @Override
    public Field getLineFromField() {
        return getField(lineFromField);
    }

    @Override
    public void setLineFromField(final Field field) {
        lineFromField.setSelectedItem(field);
    }

    @Override
    public Field getColFromField() {
        return getField(colFromField);
    }

    @Override
    public void setColFromField(final Field field) {
        colFromField.setSelectedItem(field);
    }

    @Override
    public Field getLineToField() {
        return getField(lineToField);
    }

    @Override
    public void setLineToField(final Field field) {
        lineToField.setSelectedItem(field);
    }

    @Override
    public Field getColToField() {
        return getField(colToField);
    }

    @Override
    public void setColToField(final Field field) {
        colToField.setSelectedItem(field);
    }

    private Field getField(final ItemListBox<HasDisplayValue> ctrl) {
        if (ctrl.getSelectedItem() == null || NONE.equals(ctrl.getSelectedItem())) {
            return null;
        }
        return (Field) ctrl.getSelectedItem();
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
        return this.showAsHtml.getBooleanValue();
    }

    @Override
    public void setShowAsHtml(final boolean showAsHtml) {
        this.showAsHtml.setBooleanValue(showAsHtml);
    }

    @Override
    public boolean isShowStepping() {
        return this.showStepping.getBooleanValue();
    }

    @Override
    public void setShowStepping(final boolean showStepping) {
        this.showStepping.setBooleanValue(showStepping);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    public interface Binder extends UiBinder<Widget, BasicTextSettingsViewImpl> {
    }
}
