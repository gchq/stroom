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
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.DirtyTracker;
import stroom.widget.component.client.Listeners;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class DocPresenter<V extends View, D>
        extends MyPresenterWidget<V>
        implements HasDocumentRead<D>, HasDocumentWrite<D>, HasClose, ChangeUiHandlers {

    private D entity;
    private boolean dirty;
    // Provided to everything this presenter owns, however deep, and set as the document is read:
    // which document it is. Whether it may be changed is provided once its opener says (setReadOnly);
    // until then it follows its owner's, e.g. as one of another document's tabs
    private final ContextValue<DocRef> documentContext = ContextValue.of(null);
    // Told when the document becomes dirty or clean, e.g. to show it in a window's title
    private final Listeners<Boolean> dirtyListeners = new Listeners<>();

    public DocPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
        provide(Contexts.DOCUMENT, documentContext);
        // What this owns tells it of changes, however deep (markChanged)
        provide(Contexts.DIRTY, ContextValue.<DirtyTracker>of(this::onChange));
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
            dirtyListeners.fire(dirty);
            // A part of a document (e.g. one of its tabs) tells the document
            markChanged();
        }
    }

    public final boolean isDirty() {
        return dirty;
    }

    protected void onDirty() {

    }

    /// Reads the document, read only or not as its opener said ([#setReadOnly(boolean)]), or as its
    /// owner's is for one that is part of another document.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    @Override
    public final void read(final DocRef docRef, final D document) {
        this.entity = document;
        setDirty(false);
        documentContext.set(docRef);
        if (docRef != null && document != null) {
            onRead(docRef, document);
        }
    }

    @Override
    public final D write(final D document) {
        return onWrite(document);
    }

    /// @return Whether the document may not be changed: as its opener said, or as its owner's is
    ///         (only ever narrowing it), and read only until either says otherwise.
    public final boolean isReadOnly() {
        return context(Contexts.READ_ONLY);
    }

    /// Says whether the document may be changed, e.g. from the user's permissions and its lock, before
    /// it is read (again), or for a presenter embedded in another screen and given its settings
    /// rather than a document. Everything this presenter owns follows it ([Contexts#READ_ONLY]).
    ///
    /// @param readOnly Whether it may not be changed.
    public final void setReadOnly(final boolean readOnly) {
        getComponentNode().setProvided(Contexts.READ_ONLY, readOnly);
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

    /// Shows the document. Whether it may be changed is [#isReadOnly()], which the parts this owns
    /// watch for themselves ([Contexts#READ_ONLY]).
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    protected abstract void onRead(DocRef docRef, D document);

    protected abstract D onWrite(D document);

    /// Listens for the document becoming dirty or clean, until this presenter is disposed or the
    /// registration removed. A part of the document tells the document itself, with no listener.
    ///
    /// @param listener Told whether it is now dirty.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addDirtyListener(final Consumer<Boolean> listener) {
        return dirtyListeners.add(listener);
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
