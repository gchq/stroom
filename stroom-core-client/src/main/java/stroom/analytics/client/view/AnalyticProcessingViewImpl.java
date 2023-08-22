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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AnalyticProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.client.presenter.AnalyticProcessingUiHandlers;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.Button;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class AnalyticProcessingViewImpl
        extends ViewWithUiHandlers<AnalyticProcessingUiHandlers>
        implements AnalyticProcessingView {

    private final Widget widget;

    @UiField
    SimplePanel queryEditorContainer;
    @UiField
    CustomCheckBox enabled;
    @UiField
    SelectionBox<String> node;
    @UiField
    SelectionBox<AnalyticProcessType> processingType;
    @UiField
    FormGroup analyticProcessingMinMetaCreateTimeMs;
    @UiField
    MyDateBox minMetaCreateTimeMs;
    @UiField
    FormGroup analyticProcessingMaxMetaCreateTimeMs;
    @UiField
    MyDateBox maxMetaCreateTimeMs;
    @UiField
    FormGroup analyticProcessingMinEventTimeMs;
    @UiField
    MyDateBox minEventTimeMs;
    @UiField
    FormGroup analyticProcessingMaxEventTimeMs;
    @UiField
    MyDateBox maxEventTimeMs;
    @UiField
    FormGroup queryFrequencyFormGroup;
    @UiField
    DurationPicker queryFrequency;
    @UiField
    FormGroup timeToWaitForDataFormGroup;
    @UiField
    DurationPicker timeToWaitForData;
    @UiField
    FormGroup dataRetentionFormGroup;
    @UiField
    DurationPicker dataRetention;
    @UiField
    SimplePanel info;
    @UiField
    Button refresh;

    private String selectedNode;

    @Inject
    public AnalyticProcessingViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        refresh.setIcon(SvgImage.REFRESH);

        processingType.addItem(AnalyticProcessType.STREAMING);
        processingType.addItem(AnalyticProcessType.TABLE_BUILDER);
        processingType.addItem(AnalyticProcessType.SCHEDULED_QUERY);

        updateProcessingType(null);
        queryFrequency.setValue(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build());
        timeToWaitForData.setValue(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build());
        dataRetention.setValue(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setQueryEditorView(final View view) {
        queryEditorContainer.setWidget(view.asWidget());
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public void setNodes(final List<String> nodes) {
        this.node.clear();
        this.node.addItems(nodes);
        if (selectedNode == null) {
            if (nodes.size() > 0) {
                this.node.setValue(nodes.get(0));
            }
        } else {
            this.node.setValue(selectedNode);
            selectedNode = null;
        }
    }

    @Override
    public String getNode() {
        return this.node.getValue();
    }

    @Override
    public void setNode(final String node) {
        if (node != null) {
            selectedNode = node;
            this.node.setValue(node);
        }
    }

    @Override
    public AnalyticProcessType getProcessingType() {
        return this.processingType.getValue();
    }

    @Override
    public void setProcessingType(final AnalyticProcessType analyticProcessType) {
        this.processingType.setValue(analyticProcessType);
        updateProcessingType(processingType.getValue());
    }

    @Override
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs.setMilliseconds(minMetaCreateTimeMs);
    }

    @Override
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setMilliseconds(maxMetaCreateTimeMs);
    }

    @Override
    public Long getMinEventTimeMs() {
        return minEventTimeMs.getMilliseconds();
    }

    @Override
    public void setMinEventTimeMs(final Long minEventTimeMs) {
        this.minEventTimeMs.setMilliseconds(minEventTimeMs);
    }

    @Override
    public Long getMaxEventTimeMs() {
        return maxEventTimeMs.getMilliseconds();
    }

    @Override
    public void setMaxEventTimeMs(final Long maxEventTimeMs) {
        this.maxEventTimeMs.setMilliseconds(maxEventTimeMs);
    }

    @Override
    public SimpleDuration getQueryFrequency() {
        return this.queryFrequency.getValue();
    }

    @Override
    public void setQueryFrequency(final SimpleDuration queryFrequency) {
        if (queryFrequency != null) {
            this.queryFrequency.setValue(queryFrequency);
        }
    }

    @Override
    public SimpleDuration getTimeToWaitForData() {
        return this.timeToWaitForData.getValue();
    }

    @Override
    public void setTimeToWaitForData(final SimpleDuration timeToWaitForData) {
        if (timeToWaitForData != null) {
            this.timeToWaitForData.setValue(timeToWaitForData);
        }
    }

    @Override
    public SimpleDuration getDataRetention() {
        return dataRetention.getValue();
    }

    @Override
    public void setDataRetention(final SimpleDuration dataRetention) {
        if (dataRetention != null) {
            this.dataRetention.setValue(dataRetention);
        }
    }

    @Override
    public void setInfo(final SafeHtml info) {
        this.info.setWidget(new HTML(info));
    }

    private void updateProcessingType(final AnalyticProcessType analyticProcessType) {
        analyticProcessingMinMetaCreateTimeMs.setVisible(false);
        analyticProcessingMaxMetaCreateTimeMs.setVisible(false);
        analyticProcessingMinEventTimeMs.setVisible(false);
        analyticProcessingMaxEventTimeMs.setVisible(false);
        queryFrequencyFormGroup.setVisible(false);
        timeToWaitForDataFormGroup.setVisible(false);
        dataRetentionFormGroup.setVisible(false);

        if (analyticProcessType != null) {
            switch (analyticProcessType) {
                case STREAMING:
                    analyticProcessingMinMetaCreateTimeMs.setVisible(true);
                    analyticProcessingMaxMetaCreateTimeMs.setVisible(true);
                    break;
                case TABLE_BUILDER:
                    analyticProcessingMinMetaCreateTimeMs.setVisible(true);
                    analyticProcessingMaxMetaCreateTimeMs.setVisible(true);
                    timeToWaitForDataFormGroup.setVisible(true);
                    dataRetentionFormGroup.setVisible(true);
                    timeToWaitForDataFormGroup.setLabel("Aggregation Period");
                    break;
                case SCHEDULED_QUERY:
                    analyticProcessingMinEventTimeMs.setVisible(true);
                    analyticProcessingMaxEventTimeMs.setVisible(true);
                    queryFrequencyFormGroup.setVisible(true);
                    timeToWaitForDataFormGroup.setVisible(true);
                    timeToWaitForDataFormGroup.setLabel("Time To Wait For Data To Be Indexed");
                    break;
            }
        }
    }

    @UiHandler("enabled")
    public void onEnabled(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("node")
    public void onNode(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("processingType")
    public void onProcessingType(final ValueChangeEvent<AnalyticProcessType> event) {
        updateProcessingType(processingType.getValue());
        getUiHandlers().onProcessingTypeChange();
    }

    @UiHandler("minMetaCreateTimeMs")
    public void onMinMetaCreateTimeMs(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("maxMetaCreateTimeMs")
    public void onMaxMetaCreateTimeMs(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("minEventTimeMs")
    public void onMinEventTimeMs(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("maxEventTimeMs")
    public void onMaxEventTimeMs(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("queryFrequency")
    public void onQueryFrequency(final ValueChangeEvent<SimpleDuration> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("timeToWaitForData")
    public void onTimeToWaitForData(final ValueChangeEvent<SimpleDuration> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("dataRetention")
    public void onDataRetention(final ValueChangeEvent<SimpleDuration> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("refresh")
    public void onRefreshButtonClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().onRefreshProcessingStatus();
            }
        }
    }

    public interface Binder extends UiBinder<Widget, AnalyticProcessingViewImpl> {

    }
}
