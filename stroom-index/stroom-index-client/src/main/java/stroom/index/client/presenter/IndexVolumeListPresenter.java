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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.index.shared.FetchIndexVolumesAction;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.SaveIndexVolumesAction;
import stroom.node.client.presenter.VolumeListPresenter;
import stroom.node.client.view.WrapperView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO replace with iFrame
public class IndexVolumeListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<IndexDoc>, HasWrite<IndexDoc>, HasDirtyHandlers {
    private final ClientDispatchAsync dispatcher;

    private DocRef docRef;
    private List<IndexVolume> volumes;

    @Inject
    public IndexVolumeListPresenter(final EventBus eventBus,
                                    final WrapperView view,
                                    final VolumeListPresenter volumeListPresenter,
                                    final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.dispatcher = dispatcher;

        view.setView(volumeListPresenter.getView());
    }

    @Override
    public void read(final DocRef docRef, final IndexDoc index) {
        this.docRef = docRef;
        volumes = new ArrayList<>();
        if (index != null) {
            dispatcher.exec(new FetchIndexVolumesAction(index.getVolumeGroupName())).onSuccess(result -> {
                volumes.addAll(result);
                sortVolumes();
            });
        }
    }

    private void sortVolumes() {
        volumes.sort(Comparator.comparing(IndexVolume::getPath));
    }

    @Override
    public void write(final IndexDoc index) {
        final Set<IndexVolume> set = new HashSet<>(volumes);
        dispatcher.exec(new SaveIndexVolumesAction(docRef, set));
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
