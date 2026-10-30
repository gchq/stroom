/*
 * Copyright 2020 Crown Copyright
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

package stroom.index.client.component;

import stroom.index.client.model.SupportedRetentionAge;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A Lucene index's Settings tab: its shards and partitions, its time field, how long its data is
/// kept and where it is written, its default extraction pipeline, whose picker its controller
/// ([IndexSettingsController]) attaches to, and a button to delete all its data.
public class IndexSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

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
    DropDownBox defaultExtractionPipeline;
    @UiField
    Button reset;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public IndexSettings() {
        initWidget(BINDER.createAndBindUi(this));

        partitionBy.addItem(PartitionBy.YEAR);
        partitionBy.addItem(PartitionBy.MONTH);
        partitionBy.addItem(PartitionBy.WEEK);
        partitionBy.addItem(PartitionBy.DAY);

        maxDocsPerShard.setValue(1000000000L);
        shardsPerPartition.setValue(1L);
        partitionSize.setValue(1L);

        maxDocsPerShard.addValueChangeHandler(e -> onChange.run());
        partitionBy.addValueChangeHandler(e -> onChange.run());
        partitionSize.addValueChangeHandler(e -> onChange.run());
        shardsPerPartition.addValueChangeHandler(e -> onChange.run());
        timeField.addKeyDownHandler(e -> onChange.run());
        retentionAge.addValueChangeHandler(e -> onChange.run());
        volumeGroups.addValueChangeHandler(e -> onChange.run());
    }

    /// @return The default extraction pipeline's picker, for the controller to attach to.
    public DropDownBox getDefaultExtractionPipelineBox() {
        return defaultExtractionPipeline;
    }

    /// Shows an index's settings, but for its volume group and its pipeline.
    ///
    /// @param index The index.
    public void read(final LuceneIndexDoc index) {
        maxDocsPerShard.setValue(index.getMaxDocsPerShard());
        shardsPerPartition.setValue(index.getShardsPerPartition());
        partitionBy.setValue(index.getPartitionBy());
        partitionSize.setValue(index.getPartitionSize());
        timeField.setValue(index.getTimeField());
        retentionAge.clear();
        retentionAge.addItems(SupportedRetentionAge.values());
        retentionAge.setValue(SupportedRetentionAge.get(index.getRetentionDayAge()));
    }

    /// Offers volume groups to write the index to.
    ///
    /// @param names    The names of the groups.
    /// @param selected The name of the index's group, or null or empty for none.
    public void setVolumeGroups(final List<String> names, final String selected) {
        volumeGroups.clear();
        volumeGroups.addItem("");
        volumeGroups.addItems(names);
        if (NullSafe.isNonEmptyString(selected)) {
            volumeGroups.setValue(selected);
        }
    }

    /// Writes the settings shown, but for the pipeline, into an index's builder.
    ///
    /// @param builder The index's builder.
    /// @return The builder.
    public LuceneIndexDoc.Builder write(final LuceneIndexDoc.Builder builder) {
        final String volumeGroupName = volumeGroups.getValue();
        return builder
                .maxDocsPerShard(maxDocsPerShard.getIntValue())
                .shardsPerPartition(shardsPerPartition.getIntValue())
                .partitionBy(partitionBy.getValue())
                .partitionSize(partitionSize.getIntValue())
                .timeField(timeField.getValue())
                .retentionDayAge(retentionAge.getValue().getDays())
                .volumeGroupName(NullSafe.isEmptyString(volumeGroupName)
                        ? null
                        : volumeGroupName);
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onReset What to run when Reset Data is pressed.
    public void setOnReset(final Runnable onReset) {
        reset.setOnClick(onReset);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        maxDocsPerShard.setReadOnly(readOnly);
        partitionBy.setReadOnly(readOnly);
        partitionSize.setReadOnly(readOnly);
        shardsPerPartition.setReadOnly(readOnly);
        retentionAge.setReadOnly(readOnly);
        volumeGroups.setReadOnly(readOnly);
        reset.setEnabled(!readOnly);
        timeField.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexSettings> {

    }
}
