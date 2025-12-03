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

package stroom.dashboard.client.vis;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.vis.BasicVisSettingsPresenter.BasicVisSettingsView;
import stroom.item.client.SelectionBox;

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

import java.util.List;

public class BasicVisSettingsViewImpl extends ViewWithUiHandlers<BasicVisSettingsUiHandlers>
        implements BasicVisSettingsView {

    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<Component> table;
    @UiField
    SimplePanel visualisation;

    @Inject
    public BasicVisSettingsViewImpl(final Binder binder) {
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
        final Component table = getTable();

        this.table.clear();
        this.table.addItems(tableList);

        // Reselect table id.
        setTable(table);
    }

    @Override
    public Component getTable() {
        return this.table.getValue();
    }

    @Override
    public void setTable(final Component table) {
        this.table.setValue(table);
    }

    @Override
    public void setVisualisationView(final View view) {
        final Widget widget = view.asWidget();
        widget.setWidth("100%");
        this.visualisation.setWidget(widget);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    @UiHandler("table")
    public void onTableValueChange(final ValueChangeEvent<Component> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTableChange();
        }
    }

    public interface Binder extends UiBinder<Widget, BasicVisSettingsViewImpl> {

    }
}
