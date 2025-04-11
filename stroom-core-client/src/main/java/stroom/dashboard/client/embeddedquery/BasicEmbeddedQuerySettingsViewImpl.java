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

package stroom.dashboard.client.embeddedquery;

import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

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

public class BasicEmbeddedQuerySettingsViewImpl
        extends ViewImpl
        implements BasicEmbeddedQuerySettingsView {

    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SimplePanel queryRef;
    @UiField
    CustomCheckBox queryOnOpen;
    @UiField
    CustomCheckBox autoRefresh;
    @UiField
    TextBox refreshInterval;
    @UiField
    ValueSpinner pageSize;

    @Inject
    public BasicEmbeddedQuerySettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        pageSize.setValue(100);
        pageSize.setMin(1);
        pageSize.setMax(10000);
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
    public void setQuerySelectionView(final View view) {
        final Widget w = view.asWidget();
        w.getElement().getStyle().setWidth(100, Unit.PCT);
        w.getElement().getStyle().setMargin(0, Unit.PX);
        queryRef.setWidget(w);
    }

    @Override
    public boolean isQueryOnOpen() {
        return queryOnOpen.getValue();
    }

    @Override
    public void setQueryOnOpen(final boolean queryOnOpen) {
        this.queryOnOpen.setValue(queryOnOpen);
    }

    @Override
    public boolean isAutoRefresh() {
        return autoRefresh.getValue();
    }

    @Override
    public void setAutoRefresh(final boolean autoRefresh) {
        this.autoRefresh.setValue(autoRefresh);
    }

    @Override
    public String getRefreshInterval() {
        return this.refreshInterval.getText();
    }

    @Override
    public void setRefreshInterval(final String refreshInterval) {
        this.refreshInterval.setText(refreshInterval);
    }

    @Override
    public int getPageSize() {
        return pageSize.getIntValue();
    }

    @Override
    public void setPageSize(final int pageSize) {
        this.pageSize.setValue(pageSize);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    public interface Binder extends UiBinder<Widget, BasicEmbeddedQuerySettingsViewImpl> {

    }
}
