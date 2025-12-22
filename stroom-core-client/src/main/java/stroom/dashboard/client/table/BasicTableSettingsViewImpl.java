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

package stroom.dashboard.client.table;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.BasicTableSettingsPresenter.BasicTableSettingsView;
import stroom.item.client.SelectionBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

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

import java.util.List;

public class BasicTableSettingsViewImpl
        extends ViewWithUiHandlers<BasicTableSettingsUihandlers>
        implements BasicTableSettingsView {

    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<Component> query;
    @UiField
    CustomCheckBox extractValues;
    @UiField
    CustomCheckBox useDefaultExtractionPipeline;
    @UiField
    SimplePanel pipeline;
    @UiField
    TextBox maxResults;
    @UiField
    ValueSpinner pageSize;
    @UiField
    CustomCheckBox showDetail;
    @UiField
    CustomCheckBox overrideMaxStringFieldLength;
    @UiField
    ValueSpinner maxStringFieldLength;

    @Inject
    public BasicTableSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        pageSize.setValue(100);
        pageSize.setMin(1);
        pageSize.setMax(10000);
        maxStringFieldLength.setMax(Long.MAX_VALUE);
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
    public void setQueryList(final List<Component> queryList) {
        final Component query = getQuery();

        this.query.clear();
        this.query.addItems(queryList);

        // Reselect query id.
        setQuery(query);
    }

    @Override
    public Component getQuery() {
        return this.query.getValue();
    }

    @Override
    public void setQuery(final Component query) {
        this.query.setValue(query);
    }

    @Override
    public boolean isExtractValues() {
        return this.extractValues.getValue();
    }

    @Override
    public void setExtractValues(final boolean extractValues) {
        this.extractValues.setValue(extractValues);
    }

    @Override
    public boolean isUseDefaultExtractionPipeline() {
        return useDefaultExtractionPipeline.getValue();
    }

    @Override
    public void setUseDefaultExtractionPipeline(final boolean extractValues) {
        this.useDefaultExtractionPipeline.setValue(extractValues);
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
    public int getPageSize() {
        return pageSize.getIntValue();
    }

    @Override
    public void setPageSize(final int pageSize) {
        this.pageSize.setValue(pageSize);
    }

    @Override
    public boolean isShowDetail() {
        return this.showDetail.getValue();
    }

    @Override
    public void setShowDetail(final boolean showDetail) {
        this.showDetail.setValue(showDetail);
    }

    @Override
    public void setMaxStringFieldLength(final Integer maxStringFieldLength) {
        this.maxStringFieldLength.setValue(maxStringFieldLength);
    }

    @Override
    public Integer getMaxStringFieldLength() {
        return maxStringFieldLength == null ? null : maxStringFieldLength.getIntValue();
    }

    @Override
    public void setOverrideMaxStringFieldLength(final boolean overrideMaxStringFieldLength) {
        this.overrideMaxStringFieldLength.setValue(overrideMaxStringFieldLength);
    }

    @Override
    public boolean isOverrideMaxStringFieldLength() {
        return overrideMaxStringFieldLength.getValue();
    }

    @Override
    public void enableMaxStringFieldLength(final boolean enable) {
        maxStringFieldLength.setEnabled(enable);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    @UiHandler("useDefaultExtractionPipeline")
    public void onUseDefaultExtractionPipeline(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onUseDefaultExtractionPipeline(useDefaultExtractionPipeline.getValue());
    }

    @UiHandler("overrideMaxStringFieldLength")
    public void overrideMaxStringFieldLength(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onOverrideMaxStringFieldLength(overrideMaxStringFieldLength.getValue());
    }

    public interface Binder extends UiBinder<Widget, BasicTableSettingsViewImpl> {

    }
}
