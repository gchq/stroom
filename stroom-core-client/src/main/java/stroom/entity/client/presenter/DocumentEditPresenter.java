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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public abstract class DocumentEditPresenter<V extends View, D> extends MyPresenterWidget<V>
        implements HasDocumentRead<D>, HasDocumentWrite<D>, HasDirtyHandlers, HasClose {

    private D entity;
    private boolean dirty;
    private boolean reading;
    private boolean readOnly = true;

    public DocumentEditPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    private void setDirty(final boolean dirty, final boolean force) {
        if (!isReadOnly()) {
            if (!reading && (force || this.dirty != dirty)) {
                this.dirty = dirty;
                DirtyEvent.fire(this, dirty);
                onDirty(dirty);
            }
        }
    }

    public void onDirty(final boolean dirty) {
    }

    public boolean isDirty() {
        return !readOnly && dirty;
    }

    public void setDirty(final boolean dirty) {
        setDirty(dirty, false);
    }

    @Override
    public final void read(final DocRef docRef, final D document, final boolean readOnly) {
        this.entity = document;
        this.readOnly = readOnly;
        if (getView() instanceof ReadOnlyChangeHandler) {
            final ReadOnlyChangeHandler changeHandler = (ReadOnlyChangeHandler) getView();
            changeHandler.onReadOnly(readOnly);
        }

        if (docRef != null && document != null) {
            reading = true;
            onRead(docRef, document, readOnly);
            reading = false;
            setDirty(false, true);
        }
    }

    @Override
    public final D write(final D document) {
        return onWrite(document);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Called when an attempt is made to close this presenter
     */
    @Override
    public void onClose() {
    }

    public D getEntity() {
        return entity;
    }

    protected abstract void onRead(DocRef docRef, D document, boolean readOnly);

    protected abstract D onWrite(D document);

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
