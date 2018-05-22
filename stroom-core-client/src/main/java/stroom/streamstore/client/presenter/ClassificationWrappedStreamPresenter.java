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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.IdSet;
import stroom.docref.DocRef;
import stroom.docref.SharedObject;

public class ClassificationWrappedStreamPresenter extends ClassificationWrapperPresenter
        implements HasDataSelectionHandlers<IdSet>, HasDocumentRead<SharedObject> {
    private final StreamPresenter streamPresenter;

    @Inject
    public ClassificationWrappedStreamPresenter(final EventBus eventBus, final ClassificationWrapperView view,
                                                final StreamPresenter streamPresenter) {
        super(eventBus, view);
        this.streamPresenter = streamPresenter;
        streamPresenter.setClassificationUiHandlers(this);

        setInSlot(ClassificationWrapperView.CONTENT, streamPresenter);
    }

    @Override
    public void read(final DocRef docRef, final SharedObject entity) {
        streamPresenter.read(docRef, entity);
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(DataSelectionHandler<IdSet> handler) {
        return streamPresenter.addDataSelectionHandler(handler);
    }
}
