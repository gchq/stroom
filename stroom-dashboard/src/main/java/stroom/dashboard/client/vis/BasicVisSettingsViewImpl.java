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

package stroom.dashboard.client.vis;

import java.util.List;

import com.google.gwt.event.dom.client.ChangeEvent;
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

import stroom.dashboard.client.vis.BasicVisSettingsPresenter.BasicVisSettingsView;
import stroom.item.client.StringListBox;

public class BasicVisSettingsViewImpl extends ViewWithUiHandlers<BasicVisSettingsUiHandlers>
        implements BasicVisSettingsView {
    public interface Binder extends UiBinder<Widget, BasicVisSettingsViewImpl> {
    }

    private final Widget widget;

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    StringListBox tableId;
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
    public void setTableIdList(final List<String> tableIdList) {
        final String tableId = getTableId();

        this.tableId.clear();
        this.tableId.addItems(tableIdList);

        // Reselect table id.
        setTableId(tableId);
    }

    @Override
    public void setTableId(final String tableId) {
        this.tableId.setSelected(tableId);
    }

    @Override
    public String getTableId() {
        return tableId.getSelected();
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

    @UiHandler("tableId")
    public void onTableIdChange(final ChangeEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTableIdChange();
        }
    }
}
