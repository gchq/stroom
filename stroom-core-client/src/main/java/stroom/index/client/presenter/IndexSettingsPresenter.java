/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.index.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.client.presenter.SupportedRetentionAge;
import stroom.index.client.presenter.IndexSettingsPresenter.IndexSettingsView;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.item.client.SelectionBox;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermissionNames;

import com.google.gwt.core.shared.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.stream.Collectors;

public class IndexSettingsPresenter extends DocumentEditPresenter<IndexSettingsView, LuceneIndexDoc>
        implements IndexSettingsUiHandlers {

    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final RestFactory restFactory;
    private final DocSelectionBoxPresenter pipelinePresenter;

    @Inject
    public IndexSettingsPresenter(final EventBus eventBus,
                                  final IndexSettingsView view,
                                  final DocSelectionBoxPresenter pipelinePresenter,
                                  final RestFactory restFactory) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;
        this.restFactory = restFactory;

        pipelinePresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.READ);

        view.setUiHandlers(this);
        view.setDefaultExtractionPipelineView(pipelinePresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(pipelinePresenter.addDataSelectionHandler(selection -> setDirty(true)));
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    protected void onRead(final DocRef docRef, final LuceneIndexDoc index, final boolean readOnly) {
        getView().setMaxDocsPerShard(index.getMaxDocsPerShard());
        getView().setShardsPerPartition(index.getShardsPerPartition());
        getView().setPartitionBy(index.getPartitionBy());
        getView().setPartitionSize(index.getPartitionSize());
        getView().setTimeField(index.getTimeField());
        updateRetentionAge(SupportedRetentionAge.get(index.getRetentionDayAge()));
        updateGroupList(index.getVolumeGroupName());
        pipelinePresenter.setSelectedEntityReference(index.getDefaultExtractionPipeline());
    }

    @Override
    protected LuceneIndexDoc onWrite(final LuceneIndexDoc index) {
        index.setMaxDocsPerShard(getView().getMaxDocsPerShard());
        index.setShardsPerPartition(getView().getShardsPerPartition());
        index.setPartitionBy(getView().getPartitionBy());
        index.setPartitionSize(getView().getPartitionSize());
        index.setTimeField(getView().getTimeField());
        index.setRetentionDayAge(getView().getRetentionAge().getValue().getDays());

        String volumeGroupName = getView().getVolumeGroups().getValue();
        if (volumeGroupName != null && volumeGroupName.length() == 0) {
            volumeGroupName = null;
        }
        index.setVolumeGroupName(volumeGroupName);
        index.setDefaultExtractionPipeline(pipelinePresenter.getSelectedEntityReference());
        return index;
    }

    private void updateRetentionAge(final SupportedRetentionAge selected) {
        getView().getRetentionAge().clear();
        getView().getRetentionAge().addItems(SupportedRetentionAge.values());
        getView().getRetentionAge().setValue(selected);
    }

    private void updateGroupList(final String selected) {
        restFactory
                .create(INDEX_VOLUME_GROUP_RESOURCE)
                .method(res -> res.find(new ExpressionCriteria()))
                .onSuccess(result -> {
                    final List<String> volumeGroupNames = result
                            .getValues()
                            .stream()
                            .map(IndexVolumeGroup::getName)
                            .collect(Collectors.toList());

                    SelectionBox<String> listBox = getView().getVolumeGroups();
                    listBox.clear();
                    listBox.addItem("");
                    listBox.addItems(volumeGroupNames);
                    if (selected != null && !selected.isEmpty()) {
                        listBox.setValue(selected);
                    }
                })
                .taskListener(this)
                .exec();
    }

    public interface IndexSettingsView extends View, ReadOnlyChangeHandler, HasUiHandlers<IndexSettingsUiHandlers> {

        int getMaxDocsPerShard();

        void setMaxDocsPerShard(int maxDocsPerShard);

        int getShardsPerPartition();

        void setShardsPerPartition(int shardsPerPartition);

        int getPartitionSize();

        void setPartitionSize(int size);

        PartitionBy getPartitionBy();

        void setPartitionBy(PartitionBy partitionBy);

        String getTimeField();

        void setTimeField(String partitionTimeField);

        SelectionBox<SupportedRetentionAge> getRetentionAge();

        SelectionBox<String> getVolumeGroups();

        void setDefaultExtractionPipelineView(View view);
    }
}
