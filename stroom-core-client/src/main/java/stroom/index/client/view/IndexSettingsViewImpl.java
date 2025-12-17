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

package stroom.index.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.feed.client.presenter.SupportedRetentionAge;
import stroom.index.client.presenter.IndexSettingsPresenter.IndexSettingsView;
import stroom.index.client.presenter.IndexSettingsUiHandlers;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.item.client.SelectionBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.KeyDownEvent;
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

public class IndexSettingsViewImpl extends ViewWithUiHandlers<IndexSettingsUiHandlers>
        implements IndexSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    ValueSpinner maxDocsPerShard;
    @UiField
    SelectionBox<PartitionBy> partitionBy;
    @UiField
    ValueSpinner partitionSize;
    @UiField
    ValueSpinner shardsPerPartition;
    @UiField
    TextBox timeField;
    @UiField
    SelectionBox<SupportedRetentionAge> retentionAge;
    @UiField
    SelectionBox<String> volumeGroups;
    @UiField
    SimplePanel defaultExtractionPipeline;

    @Inject
    public IndexSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        partitionBy.setNonSelectString("No partition");
        partitionBy.addItem(PartitionBy.YEAR);
        partitionBy.addItem(PartitionBy.MONTH);
        partitionBy.addItem(PartitionBy.WEEK);
        partitionBy.addItem(PartitionBy.DAY);

        maxDocsPerShard.setValue(1000000000L);
        maxDocsPerShard.setMin(1000L);
        maxDocsPerShard.setMax(10000000000L);

        shardsPerPartition.setValue(1L);
        shardsPerPartition.setMin(1L);
        shardsPerPartition.setMax(100L);

        partitionSize.setValue(1L);
        partitionSize.setMin(1L);
        partitionSize.setMax(100L);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public int getMaxDocsPerShard() {
        return this.maxDocsPerShard.getIntValue();
    }

    @Override
    public void setMaxDocsPerShard(final int maxDocsPerShard) {
        this.maxDocsPerShard.setValue(maxDocsPerShard);
    }

    @Override
    public int getShardsPerPartition() {
        return this.shardsPerPartition.getIntValue();
    }

    @Override
    public void setShardsPerPartition(final int shardsPerPartition) {
        this.shardsPerPartition.setValue(shardsPerPartition);
    }

    @Override
    public PartitionBy getPartitionBy() {
        return this.partitionBy.getValue();
    }

    @Override
    public void setPartitionBy(final PartitionBy partitionBy) {
        this.partitionBy.setValue(partitionBy);
    }

    @Override
    public int getPartitionSize() {
        return this.partitionSize.getIntValue();
    }

    @Override
    public void setPartitionSize(final int size) {
        this.partitionSize.setValue(size);
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
    public SelectionBox<SupportedRetentionAge> getRetentionAge() {
        return retentionAge;
    }

    @Override
    public SelectionBox<String> getVolumeGroups() {
        return volumeGroups;
    }

    @Override
    public void setDefaultExtractionPipelineView(final View view) {
        this.defaultExtractionPipeline.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        maxDocsPerShard.setEnabled(!readOnly);
        partitionBy.setEnabled(!readOnly);
        partitionSize.setEnabled(!readOnly);
        shardsPerPartition.setEnabled(!readOnly);
        retentionAge.setEnabled(!readOnly);
        volumeGroups.setEnabled(!readOnly);
    }

    @UiHandler("maxDocsPerShard")
    public void onMaxDocsPerShardValueChange(final ValueChangeEvent<Long> e) {
        fireChange();
    }

    @UiHandler("partitionBy")
    public void onPartitionByValueChange(final ValueChangeEvent<PartitionBy> e) {
        fireChange();
    }

    @UiHandler("partitionSize")
    public void onPartitionSizeValueChange(final ValueChangeEvent<Long> e) {
        fireChange();
    }

    @UiHandler("shardsPerPartition")
    public void onShardsPerPartitionValueChange(final ValueChangeEvent<Long> e) {
        fireChange();
    }

    @UiHandler("timeField")
    public void onTimeFieldKeyDown(final KeyDownEvent e) {
        fireChange();
    }

    @UiHandler("retentionAge")
    public void onRetentionAgeValueChange(final ValueChangeEvent<SupportedRetentionAge> e) {
        fireChange();
    }

    @UiHandler("volumeGroups")
    public void onVolumeGroupsValueChange(final ValueChangeEvent<String> e) {
        fireChange();
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    public interface Binder extends UiBinder<Widget, IndexSettingsViewImpl> {

    }
}
