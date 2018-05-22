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

package stroom.index.client.view;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.index.client.presenter.IndexSettingsPresenter.IndexSettingsView;
import stroom.index.client.presenter.IndexSettingsUiHandlers;
import stroom.index.shared.IndexDoc.PartitionBy;
import stroom.item.client.ItemListBox;
import stroom.pipeline.shared.SupportedRetentionAge;
import stroom.widget.layout.client.view.ResizeSimplePanel;
import stroom.widget.valuespinner.client.SpinnerEvent;
import stroom.widget.valuespinner.client.ValueSpinner;

public class IndexSettingsViewImpl extends ViewWithUiHandlers<IndexSettingsUiHandlers> implements IndexSettingsView {
    private final Widget widget;
    @UiField
    TextArea description;
    @UiField
    ValueSpinner maxDocsPerShard;
    @UiField(provided = true)
    ItemListBox<PartitionBy> partitionBy;
    @UiField
    ValueSpinner partitionSize;
    @UiField
    ValueSpinner shardsPerPartition;
    @UiField
    ItemListBox<SupportedRetentionAge> retentionAge;
    @UiField
    ResizeSimplePanel volumes;

    @Inject
    public IndexSettingsViewImpl(final Binder binder) {
        partitionBy = new ItemListBox<>("No partition");
        partitionBy.addItem(PartitionBy.YEAR);
        partitionBy.addItem(PartitionBy.MONTH);
        partitionBy.addItem(PartitionBy.WEEK);
        partitionBy.addItem(PartitionBy.DAY);

        widget = binder.createAndBindUi(this);

        maxDocsPerShard.setValue(1000000000L);
        maxDocsPerShard.setMin(1000L);
        maxDocsPerShard.setMax(10000000000L);

        shardsPerPartition.setValue(1L);
        shardsPerPartition.setMin(1L);
        shardsPerPartition.setMax(100L);

        partitionSize.setValue(1L);
        partitionSize.setMin(1L);
        partitionSize.setMax(100L);

        final SpinnerEvent.Handler spinnerHandler = new SpinnerEvent.Handler() {
            @Override
            public void onChange(final SpinnerEvent event) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onChange();
                }
            }
        };
        maxDocsPerShard.getSpinner().addSpinnerHandler(spinnerHandler);
        shardsPerPartition.getSpinner().addSpinnerHandler(spinnerHandler);
        partitionBy.addSelectionHandler(new SelectionHandler<PartitionBy>() {
            @Override
            public void onSelection(final SelectionEvent<PartitionBy> event) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onChange();
                }
            }
        });
        retentionAge.addSelectionHandler(new SelectionHandler<SupportedRetentionAge>() {
            @Override
            public void onSelection(final SelectionEvent<SupportedRetentionAge> event) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onChange();
                }
            }
        });
        partitionSize.getSpinner().addSpinnerHandler(spinnerHandler);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TextArea getDescription() {
        return description;
    }

    @Override
    public int getMaxDocsPerShard() {
        return this.maxDocsPerShard.getValue();
    }

    @Override
    public void setMaxDocsPerShard(final int maxDocsPerShard) {
        this.maxDocsPerShard.setValue(maxDocsPerShard);
    }

    @Override
    public int getShardsPerPartition() {
        return this.shardsPerPartition.getValue();
    }

    @Override
    public void setShardsPerPartition(final int shardsPerPartition) {
        this.shardsPerPartition.setValue(shardsPerPartition);
    }

    @Override
    public PartitionBy getPartitionBy() {
        return this.partitionBy.getSelectedItem();
    }

    @Override
    public void setPartitionBy(final PartitionBy partitionBy) {
        this.partitionBy.setSelectedItem(partitionBy);
    }

    @Override
    public int getPartitionSize() {
        return this.partitionSize.getValue();
    }

    @Override
    public void setPartitionSize(final int size) {
        this.partitionSize.setValue(size);
    }

    @Override
    public ItemListBox<SupportedRetentionAge> getRetentionAge() {
        return retentionAge;
    }

    @Override
    public void setVolumeList(final View view) {
        this.volumes.setWidget(view.asWidget());
    }

    public interface Binder extends UiBinder<Widget, IndexSettingsViewImpl> {
    }
}
