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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class DocPresenter<V extends View, D>
        extends MyPresenterWidget<V>
        implements HasDocumentRead<D>, HasDocumentWrite<D>, HasDirtyHandlers, HasClose, ChangeUiHandlers {

    private D entity;
    private boolean readOnly = true;
    private boolean dirty;
    // Provided to everything this presenter owns, however deep, and set as the document is read:
    // whether it may be changed (only narrowing what an owner provides) and which document it is
    private final ContextValue<Boolean> readOnlyContext = ContextValue.of(true);
    private final ContextValue<DocRef> documentContext = ContextValue.of(null);

    public DocPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
        provide(Contexts.READ_ONLY, readOnlyContext);
        provide(Contexts.DOCUMENT, documentContext);
    }

    @Override
    public final void onChange() {
        if (!isReadOnly()) {
            // Test the associated documents first as it short circuits the comparison below, which
            // for some documents is expensive and is run on every keypress in an embedded editor.
            final boolean dirty = hasAssociatedDirty() || !Objects.equals(entity, write(entity));
            setDirty(dirty);
        }
    }

    /**
     * Can be overridden if the Doc has associated components that could be dirty e.g. PipelineDoc
     */
    protected boolean hasAssociatedDirty() {
        return false;
    }

    protected void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            onDirty();
            DirtyEvent.fire(this, dirty);
        }
    }

    public final boolean isDirty() {
        return dirty;
    }

    protected void onDirty() {

    }

    @Override
    public final void read(final DocRef docRef, final D document, final boolean readOnly) {
        this.entity = document;
        this.readOnly = readOnly;
        setDirty(false);
        documentContext.set(docRef);
        readOnlyContext.set(readOnly);

        if (getView() instanceof final ReadOnlyChangeHandler changeHandler) {
            changeHandler.onReadOnly(readOnly);
        }
        if (docRef != null && document != null) {
            onRead(docRef, document, readOnly);
        }
    }

    @Override
    public final D write(final D document) {
        return onWrite(document);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /// Says whether the document may be changed without reading it, e.g. for a presenter embedded
    /// in another screen, given its settings rather than a document. Everything this presenter
    /// owns follows it ([Contexts#READ_ONLY]).
    ///
    /// @param readOnly Whether it may not be changed.
    protected final void setDocumentReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        readOnlyContext.set(readOnly);
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

    /**
     * Allows the derived class to specify a callback after the document has been written
     * to the server. Default is no callback. Override if you want a callback.
     *
     * @return null if no callback, otherwise the consumer to be called.
     */
    public BiConsumer<D, Consumer<D>> getPostSaveCallback() {
        return null;
    }

    /**
     * Allows the derived class to specify a callback after SaveAs has been invoked.
     * Default is no callback. Override if you want a callback.
     *
     * @return null if no callback, otherwise the consumer to be called.
     */
    public BiConsumer<D, Consumer<D>> getPostSaveAsCallback() {
        return null;
    }

}
