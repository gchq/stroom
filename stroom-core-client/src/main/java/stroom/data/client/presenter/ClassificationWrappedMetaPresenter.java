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

package stroom.data.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.util.shared.Selection;

public class ClassificationWrappedMetaPresenter extends ClassificationWrapperPresenter
        implements HasDataSelectionHandlers<Selection<Long>>, HasDocumentRead<Object> {
    private final MetaPresenter metaPresenter;

    @Inject
    public ClassificationWrappedMetaPresenter(final EventBus eventBus, final ClassificationWrapperView view,
                                              final MetaPresenter metaPresenter) {
        super(eventBus, view);
        this.metaPresenter = metaPresenter;
        metaPresenter.setClassificationUiHandlers(this);

        setInSlot(ClassificationWrapperView.CONTENT, metaPresenter);
    }

    @Override
    public void read(final DocRef docRef, final Object entity) {
        metaPresenter.read(docRef, entity);
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(DataSelectionHandler<Selection<Long>> handler) {
        return metaPresenter.addDataSelectionHandler(handler);
    }
}
