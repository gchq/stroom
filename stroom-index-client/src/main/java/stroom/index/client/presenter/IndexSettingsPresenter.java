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

package stroom.index.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.index.shared.Index;
import stroom.index.shared.Index.PartitionBy;
import stroom.item.client.ItemListBox;
import stroom.pipeline.shared.SupportedRetentionAge;

public class IndexSettingsPresenter extends MyPresenterWidget<IndexSettingsPresenter.IndexSettingsView>
        implements HasRead<Index>, HasWrite<Index>, HasDirtyHandlers, IndexSettingsUiHandlers {

    private final IndexVolumeListPresenter indexVolumeListPresenter;

    @Inject
    public IndexSettingsPresenter(final EventBus eventBus, final IndexSettingsView view,
            final IndexVolumeListPresenter indexVolumeListPresenter) {
        super(eventBus, view);
        this.indexVolumeListPresenter = indexVolumeListPresenter;

        view.setUiHandlers(this);
        view.getRetentionAge().addItems(SupportedRetentionAge.values());
        view.setVolumeList(indexVolumeListPresenter.getView());
    }

    @Override
    protected void onBind() {
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };
        final DirtyHandler dirtyHandler = event -> setDirty(true);

        registerHandler(getView().getDescription().addKeyDownHandler(keyDownHander));
        registerHandler(indexVolumeListPresenter.addDirtyHandler(dirtyHandler));
    }

    private void setDirty(final boolean dirty) {
        DirtyEvent.fire(IndexSettingsPresenter.this, dirty);
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public void read(final Index index) {
        if (index != null) {
            getView().getDescription().setText(index.getDescription());
            getView().setMaxDocsPerShard(index.getMaxDocsPerShard());
            getView().setShardsPerPartition(index.getShardsPerPartition());
            getView().setPartitionBy(index.getPartitionBy());
            getView().setPartitionSize(index.getPartitionSize());
            getView().getRetentionAge().setSelectedItem(SupportedRetentionAge.get(index.getRetentionDayAge()));

            indexVolumeListPresenter.read(index);
        }
    }

    @Override
    public void write(final Index index) {
        if (index != null) {
            index.setDescription(getView().getDescription().getText().trim());
            index.setMaxDocsPerShard(getView().getMaxDocsPerShard());
            index.setShardsPerPartition(getView().getShardsPerPartition());
            index.setPartitionBy(getView().getPartitionBy());
            index.setPartitionSize(getView().getPartitionSize());
            index.setRetentionDayAge(getView().getRetentionAge().getSelectedItem().getDays());

            indexVolumeListPresenter.write(index);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface IndexSettingsView extends View, HasUiHandlers<IndexSettingsUiHandlers> {
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

        void setVolumeList(View view);
    }
}
