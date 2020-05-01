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

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.client.presenter.SupportedRetentionAge;
import stroom.index.client.presenter.IndexSettingsPresenter.IndexSettingsView;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexDoc.PartitionBy;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.stream.Collectors;

public class IndexSettingsPresenter extends DocumentSettingsPresenter<IndexSettingsView, IndexDoc> implements IndexSettingsUiHandlers {
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE = GWT.create(IndexVolumeGroupResource.class);

    private final RestFactory restFactory;

    @Inject
    public IndexSettingsPresenter(final EventBus eventBus,
                                  final IndexSettingsView view,
                                  final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };
        registerHandler(getView().getDescription().addKeyDownHandler(keyDownHander));
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final IndexDoc index) {
        getView().getDescription().setText(index.getDescription());
        getView().setMaxDocsPerShard(index.getMaxDocsPerShard());
        getView().setShardsPerPartition(index.getShardsPerPartition());
        getView().setPartitionBy(index.getPartitionBy());
        getView().setPartitionSize(index.getPartitionSize());
        updateRetentionAge(SupportedRetentionAge.get(index.getRetentionDayAge()));
        updateGroupList(index.getVolumeGroupName());
    }

    @Override
    protected void onWrite(final IndexDoc index) {
        index.setDescription(getView().getDescription().getText().trim());
        index.setMaxDocsPerShard(getView().getMaxDocsPerShard());
        index.setShardsPerPartition(getView().getShardsPerPartition());
        index.setPartitionBy(getView().getPartitionBy());
        index.setPartitionSize(getView().getPartitionSize());
        index.setRetentionDayAge(getView().getRetentionAge().getSelectedItem().getDays());
        index.setVolumeGroupName(getView().getVolumeGroups().getSelected());
    }

    private void updateRetentionAge(final SupportedRetentionAge selected) {
        getView().getRetentionAge().clear();
        getView().getRetentionAge().addItems(SupportedRetentionAge.values());
        getView().getRetentionAge().setSelectedItem(selected);
    }

    private void updateGroupList(final String selected) {
        final Rest<ResultPage<IndexVolumeGroup>> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    final List<String> volumeGroupNames = result
                            .getValues()
                            .stream()
                            .map(IndexVolumeGroup::getName)
                            .collect(Collectors.toList());

                    StringListBox listBox = getView().getVolumeGroups();
                    listBox.clear();
                    listBox.addItems(volumeGroupNames);
                    if (selected != null && !selected.isEmpty()) {
                        listBox.setSelected(selected);
                    }
                })
                .call(INDEX_VOLUME_GROUP_RESOURCE)
                .find(new ExpressionCriteria());
    }

    public interface IndexSettingsView extends View, ReadOnlyChangeHandler, HasUiHandlers<IndexSettingsUiHandlers> {
        TextArea getDescription();

        int getMaxDocsPerShard();

        void setMaxDocsPerShard(int maxDocsPerShard);

        int getShardsPerPartition();

        void setShardsPerPartition(int shardsPerPartition);

        int getPartitionSize();

        void setPartitionSize(int size);

        PartitionBy getPartitionBy();

        void setPartitionBy(PartitionBy partitionBy);

        ItemListBox<SupportedRetentionAge> getRetentionAge();

        StringListBox getVolumeGroups();
    }
}
