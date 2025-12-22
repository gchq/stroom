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

package stroom.search.solr.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.item.client.SelectionBox;
import stroom.search.solr.client.presenter.SolrIndexSettingsPresenter.SolrIndexSettingsView;
import stroom.search.solr.client.presenter.SolrIndexSettingsUiHandlers;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SolrIndexSettingsViewImpl extends ViewWithUiHandlers<SolrIndexSettingsUiHandlers>
        implements SolrIndexSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox collection;
    @UiField
    SelectionBox<InstanceType> instanceType;
    @UiField
    TextArea solrUrls;
    @UiField
    CustomCheckBox useZk;
    @UiField
    TextArea zkHosts;
    @UiField
    TextArea zkPath;
    @UiField
    TextBox timeField;
    @UiField
    SimplePanel defaultExtractionPipeline;
    @UiField
    Button testConnection;
    @UiField
    SimplePanel retentionExpressionPanel;

    @Inject
    public SolrIndexSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testConnection.setIcon(SvgImage.OK);

        instanceType.addItem(InstanceType.SINGLE_NOOE);
        instanceType.addItem(InstanceType.SOLR_CLOUD);
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
    public String getCollection() {
        return collection.getText().trim();
    }

    @Override
    public void setCollection(final String collection) {
        this.collection.setText(collection);
    }

    @Override
    public InstanceType getInstanceType() {
        return instanceType.getValue();
    }

    @Override
    public void setInstanceType(final InstanceType instanceType) {
        if (instanceType == null) {
            this.instanceType.setValue(InstanceType.SINGLE_NOOE);
        } else {
            this.instanceType.setValue(instanceType);
        }
    }

    @Override
    public List<String> getSolrUrls() {
        return Arrays.stream(solrUrls.getText().split("\n")).collect(Collectors.toList());
    }

    @Override
    public void setSolrUrls(final List<String> solrUrls) {
        if (solrUrls == null) {
            this.solrUrls.setText("");
        } else {
            this.solrUrls.setText(String.join("\n", solrUrls));
        }
    }

    @Override
    public boolean isUseZk() {
        return useZk.getValue();
    }

    @Override
    public void setUseZk(final boolean useZk) {
        this.useZk.setValue(useZk);
    }

    @Override
    public List<String> getZkHosts() {
        return Arrays.stream(zkHosts.getText().split("\n")).collect(Collectors.toList());
    }

    @Override
    public void setZkHosts(final List<String> zkHosts) {
        if (zkHosts == null) {
            this.zkHosts.setText("");
        } else {
            this.zkHosts.setText(String.join("\n", zkHosts));
        }
    }

    @Override
    public String getZkPath() {
        return zkPath.getText();
    }

    @Override
    public void setZkPath(final String zkPath) {
        if (zkPath == null) {
            this.zkPath.setText("");
        } else {
            this.zkPath.setText(zkPath);
        }
    }

    @Override
    public void setRetentionExpressionView(final View view) {
        retentionExpressionPanel.setWidget(view.asWidget());
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
    public void setDefaultExtractionPipelineView(final View view) {
        this.defaultExtractionPipeline.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        instanceType.setEnabled(!readOnly);
        solrUrls.setEnabled(!readOnly);
        useZk.setEnabled(!readOnly);
        zkHosts.setEnabled(!readOnly);
        zkPath.setEnabled(!readOnly);
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestConnection(testConnection);
        }
    }

    @Override
    public void setTestingConnection(final boolean testing) {
        testConnection.setLoading(testing);
    }

    @UiHandler("collection")
    public void onCollectionKeyDown(final KeyDownEvent event) {
        fireChange();
    }

    @UiHandler("instanceType")
    public void onInstanceTypeValueChange(final ValueChangeEvent<InstanceType> event) {
        fireChange();
    }

    @UiHandler("solrUrls")
    public void onSolrUrlsKeyDown(final KeyDownEvent event) {
        fireChange();
    }

    @UiHandler("useZk")
    public void onUseZkValueChange(final ValueChangeEvent<Boolean> event) {
        fireChange();
    }

    @UiHandler("zkHosts")
    public void onZKHostsKeyDown(final KeyDownEvent event) {
        fireChange();
    }

    @UiHandler("zkPath")
    public void onZKPathKeyDown(final KeyDownEvent event) {
        fireChange();
    }

    @UiHandler("timeField")
    public void onTimeFieldKeyDown(final KeyDownEvent event) {
        fireChange();
    }

    public interface Binder extends UiBinder<Widget, SolrIndexSettingsViewImpl> {

    }
}
