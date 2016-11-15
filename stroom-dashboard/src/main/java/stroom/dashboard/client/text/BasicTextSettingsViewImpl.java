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
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.dashboard.client.text.BasicTextSettingsPresenter.BasicTextSettingsView;
import stroom.item.client.StringListBox;
import stroom.widget.tickbox.client.view.TickBox;

import java.util.List;

public class BasicTextSettingsViewImpl extends ViewImpl implements BasicTextSettingsView {
    private static final String ANY = "Any";
    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    StringListBox tableId;
    @UiField
    SimplePanel pipeline;
    @UiField
    TickBox showAsHtml;
    @Inject
    public BasicTextSettingsViewImpl(final Binder binder) {
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
        this.tableId.addItem(ANY);
        this.tableId.addItems(tableIdList);

        // Reselect table id.
        setTableId(tableId);
    }

    @Override
    public String getTableId() {
        if (ANY.equals(tableId.getSelected())) {
            return null;
        }
        return tableId.getSelected();
    }

    @Override
    public void setTableId(final String tableId) {
        if (tableId == null) {
            this.tableId.setSelected(ANY);
        } else {
            this.tableId.setSelected(tableId);
        }
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

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    public interface Binder extends UiBinder<Widget, BasicTextSettingsViewImpl> {
    }
}
