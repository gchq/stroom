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

package stroom.search.elastic.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ElasticIndexSettingsViewImpl extends ViewWithUiHandlers<ElasticIndexSettingsUiHandlers>
        implements ElasticIndexSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox indexName;
    @UiField
    ValueSpinner searchSlices;
    @UiField
    ValueSpinner searchScrollSize;
    @UiField
    SimplePanel cluster;
    @UiField
    Button testConnection;
    @UiField
    TextBox timeField;
    @UiField
    SimplePanel vectorGenerationModel;
    @UiField
    SimplePanel rerankModel;
    @UiField
    TextBox rerankTextFieldSuffix;
    @UiField
    TextBox rerankScoreMinimum;
    @UiField
    SimplePanel defaultExtractionPipeline;

    @Inject
    public ElasticIndexSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testConnection.setIcon(SvgImage.OK);

        searchSlices.setMin(1L);
        searchSlices.setMax(1000L);

        searchScrollSize.setMin(1L);
        searchScrollSize.setMax(1000000L);
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setClusterView(final View view) {
        cluster.setWidget(view.asWidget());
    }

    @Override
    public String getIndexName() {
        return indexName.getText().trim();
    }

    @Override
    public void setIndexName(final String indexName) {
        this.indexName.setText(indexName);
    }

    @Override
    public int getSearchSlices() {
        return searchSlices.getIntValue();
    }

    @Override
    public void setSearchSlices(final int searchSlices) {
        this.searchSlices.setValue(searchSlices);
    }

    @Override
    public int getSearchScrollSize() {
        return searchScrollSize.getIntValue();
    }

    @Override
    public void setSearchScrollSize(final int searchScrollSize) {
        this.searchScrollSize.setValue(searchScrollSize);
    }

    @Override
    public String getTimeField() {
        return timeField.getValue();
    }

    @Override
    public void setTimeField(final String partitionTimeField) {
        this.timeField.setValue(partitionTimeField);
    }

    @Override
    public void setVectorGenerationModelView(final View view) {
        vectorGenerationModel.setWidget(view.asWidget());
    }

    @Override
    public String getRerankTextFieldSuffix() {
        return rerankTextFieldSuffix.getValue();
    }

    @Override
    public void setRerankTextFieldSuffix(final String rerankTextFieldSuffix) {
        this.rerankTextFieldSuffix.setValue(rerankTextFieldSuffix);
    }

    @Override
    public Float getRerankScoreMinimum() {
        return Float.parseFloat(rerankScoreMinimum.getValue());
    }

    @Override
    public void setRerankScoreMinimum(final Float rerankScoreMinimum) {
        this.rerankScoreMinimum.setValue(rerankScoreMinimum.toString());
    }

    @Override
    public void setRerankModelView(final View view) {
        rerankModel.setWidget(view.asWidget());
    }

    @Override
    public void setDefaultExtractionPipelineView(final View view) {
        this.defaultExtractionPipeline.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        indexName.setEnabled(!readOnly);
        searchSlices.setEnabled(!readOnly);
        searchScrollSize.setEnabled(!readOnly);
    }

    @UiHandler("indexName")
    public void onIndexNameValueChange(final ValueChangeEvent<String> e) {
        fireChange();
    }

    @UiHandler("searchSlices")
    public void onSearchSlicesValueChange(final ValueChangeEvent<Long> e) {
        fireChange();
    }

    @UiHandler("searchScrollSize")
    public void onSearchScrollSizeValueChange(final ValueChangeEvent<Long> e) {
        fireChange();
    }

    @UiHandler("timeField")
    public void onTimeFieldValueChange(final ValueChangeEvent<String> e) {
        fireChange();
    }

    @UiHandler("rerankTextFieldSuffix")
    public void onRerankTextFieldSuffixValueChange(final ValueChangeEvent<String> e) {
        fireChange();
    }

    @UiHandler("rerankScoreMinimum")
    public void onRerankScoreMinimumValueChange(final ValueChangeEvent<String> e) {
        fireChange();
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestIndex();
        }
    }

    public interface Binder extends UiBinder<Widget, ElasticIndexSettingsViewImpl> {

    }
}
