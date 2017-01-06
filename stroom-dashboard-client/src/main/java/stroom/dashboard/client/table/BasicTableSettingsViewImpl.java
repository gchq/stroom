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

package stroom.dashboard.client.table;

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
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.BasicTableSettingsPresenter.BasicTableSettingsView;
import stroom.item.client.ItemListBox;
import stroom.widget.tickbox.client.view.TickBox;

import java.util.List;

public class BasicTableSettingsViewImpl extends ViewImpl implements BasicTableSettingsView {
    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    ItemListBox<Component> query;
    @UiField
    TickBox extractValues;
    @UiField
    SimplePanel pipeline;
    @UiField
    TextBox maxResults;
    @UiField
    TickBox showDetail;
    @Inject
    public BasicTableSettingsViewImpl(final Binder binder) {
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
    public void setQueryList(final List<Component> queryList) {
        final Component query = getQuery();

        this.query.clear();
        this.query.addItems(queryList);

        // Reselect query id.
        setQuery(query);
    }

    @Override
    public Component getQuery() {
        return this.query.getSelectedItem();
    }

    @Override
    public void setQuery(final Component query) {
        this.query.setSelectedItem(query);
    }

    @Override
    public boolean isExtractValues() {
        return this.extractValues.getBooleanValue();
    }

    @Override
    public void setExtractValues(final boolean extractValues) {
        this.extractValues.setBooleanValue(extractValues);
    }

    @Override
    public void setPipelineView(final View view) {
        final Widget widget = view.asWidget();
        widget.getElement().getStyle().setWidth(100, Unit.PCT);
        pipeline.setWidget(widget);
    }

    @Override
    public String getMaxResults() {
        return this.maxResults.getValue();
    }

    @Override
    public void setMaxResults(final String maxResults) {
        this.maxResults.setText(maxResults);
    }

    @Override
    public boolean isShowDetail() {
        return this.showDetail.getBooleanValue();
    }

    @Override
    public void setShowDetail(final boolean showDetail) {
        this.showDetail.setBooleanValue(showDetail);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    public interface Binder extends UiBinder<Widget, BasicTableSettingsViewImpl> {
    }
}
