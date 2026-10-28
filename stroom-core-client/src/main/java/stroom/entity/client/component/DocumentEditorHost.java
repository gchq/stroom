/*
 * Copyright 2026 Crown Copyright
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

package stroom.entity.client.component;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.HasSave;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.document.client.event.SaveAsDocumentEvent;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.entity.client.HasCommonTabs;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.DirtyTracker;
import stroom.widget.component.client.LayerController;
import stroom.widget.component.client.Listeners;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Opens a [DocumentEditor], or another [TabbedController] such as a folder's tabs, as a tab of
/// Stroom's, for `DocumentPlugin`, which opens, saves and closes documents. It owns the controller,
/// shows its widget, and:
///
/// * reads the document into it as the plugin reads it (when it is opened, saved, or its lock
///   changes), telling a document editor whether the user may change it;
/// * marks the tab's label while a document editor has changes that aren't saved;
/// * asks the plugin to save the document, or save a copy, when the editor's buttons are pressed.
///
/// ```java
/// @Override
/// protected DocumentEditorHost<?> createEditor() {
///     return new DocumentEditorHost<>(eventBus, editorProvider.get(), DictionaryDoc.TYPE);
/// }
/// ```
///
/// @param <D> The document's type.
public class DocumentEditorHost<D>
        extends LayerController
        implements DocumentTabData, HasSave, HasCommonTabs, TaskMonitorFactory {

    private final TabbedController<D> editor;
    private final String type;
    // Provided to everything this owns, however deep, and set as the document is read: which
    // document it is
    private final ContextValue<DocRef> documentContext = ContextValue.of(null);
    // Told when the document becomes dirty or clean, e.g. to show it in a window's title
    private final Listeners<Boolean> dirtyListeners = new Listeners<>();
    private DocRef docRef;
    private D document;
    private boolean dirty;
    private String lastLabel;

    /// @param eventBus The application's event bus.
    /// @param editor   The document's editor (or a folder's tabs), just made.
    /// @param type     The document's type, e.g. `DictionaryDoc.TYPE`, for the tab's icon.
    public DocumentEditorHost(final EventBus eventBus, final TabbedController<D> editor, final String type) {
        super(eventBus);
        provide(Contexts.DOCUMENT, documentContext);
        // What a folder's tabs or an annotation's own tell it of changes (a document editor tells
        // it itself, below)
        provide(Contexts.DIRTY, ContextValue.<DirtyTracker>of(this::onChange));
        this.editor = own(editor);
        initWidget(editor.asWidget());
        this.type = Objects.requireNonNull(type, "type");
        if (editor instanceof final DocumentEditor<D> documentEditor) {
            documentEditor.setOnDirtyChange(this::setDirty);
            documentEditor.setOnSave(this::fireSave);
            documentEditor.setOnSaveAs(() -> SaveAsDocumentEvent.fire(this, this));
        }
        listen(ContentTabSelectionChangeEvent.getType(),
                event -> editor.onContentTabVisible(event.getTabData() == this));
    }

    /// @return The controller this shows.
    public TabbedController<D> getEditor() {
        return editor;
    }

    /// Reads the document into the editor, read only or not as [#setReadOnly(boolean)] said.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    public final void read(final DocRef docRef, final D document) {
        this.document = document;
        setDirty(false);
        documentContext.set(docRef);
        if (docRef != null && document != null) {
            this.docRef = docRef;
            if (editor instanceof final DocumentEditor<D> documentEditor) {
                documentEditor.setReadOnly(isReadOnly());
            }
            editor.read(docRef, document);
            // The name may have changed (e.g. after a rename or Save As)
            refreshTabIfLabelChanged();
        }
    }

    /// Writes what the editor shows into a document, which for Save As is the new one.
    ///
    /// @param document The document to write into.
    /// @return A copy of it with what the editor shows.
    public final D write(final D document) {
        return editor.write(document);
    }

    /// @return The document as it was last read.
    public D getEntity() {
        return document;
    }

    /// @return Whether the document may not be changed: as its opener said, or as its owner's is
    /// (only ever narrowing it), and read only until either says otherwise.
    public final boolean isReadOnly() {
        return context(Contexts.READ_ONLY);
    }

    /// Says whether the document may be changed, e.g. from the user's permissions and its lock,
    /// before it is read (again). Everything the editor shows follows it ([Contexts#READ_ONLY]).
    ///
    /// @param readOnly Whether it may not be changed.
    public final void setReadOnly(final boolean readOnly) {
        getControllerNode().setProvided(Contexts.READ_ONLY, readOnly);
        if (editor instanceof final DocumentEditor<D> documentEditor) {
            documentEditor.setReadOnly(isReadOnly());
        }
    }

    @Override
    public final boolean isDirty() {
        return dirty;
    }

    /// Listens for the document becoming dirty or clean, until this is disposed or the registration
    /// removed.
    ///
    /// @param listener Told whether it is now dirty.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addDirtyListener(final Consumer<Boolean> listener) {
        return dirtyListeners.add(listener);
    }

    // What the editor's tabs tell this of changes, when it isn't a document editor (which works
    // its dirty state out itself)
    private void onChange() {
        if (!isReadOnly()) {
            setDirty(!Objects.equals(document, write(document)));
        }
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            refreshTabIfLabelChanged();
            dirtyListeners.fire(dirty);
        }
    }

    @Override
    public void save() {
        // As the editor's Save button, so the editor may ask first
        if (editor instanceof final DocumentEditor<D> documentEditor) {
            documentEditor.save();
        } else {
            fireSave();
        }
    }

    // Asks DocumentPlugin to save the document
    private void fireSave() {
        if (isDirty()) {
            SaveDocumentEvent.fire(this, this);
        }
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        return editor.handleKeyAction(action);
    }

    /// @return What to run once the document is saved, before it is read again (see
    /// [DocumentEditor#getPostSaveCallback()]), or null.
    public BiConsumer<D, Consumer<D>> getPostSaveCallback() {
        return editor instanceof final DocumentEditor<D> documentEditor
                ? documentEditor.getPostSaveCallback()
                : null;
    }

    /// @return What to run once a copy of the document is saved (see
    /// [DocumentEditor#getPostSaveAsCallback()]), or null.
    public BiConsumer<D, Consumer<D>> getPostSaveAsCallback() {
        return editor instanceof final DocumentEditor<D> documentEditor
                ? documentEditor.getPostSaveAsCallback()
                : null;
    }

    @Override
    public void selectCommonTab(final CommonDocLinkTab commonDocLinkTab) {
        editor.selectCommonTab(commonDocLinkTab);
    }

    @Override
    public DocRef getDocRef() {
        return docRef;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public SvgImage getIcon() {
        return DocumentTypeRegistry.getIcon(type);
    }

    @Override
    public String getLabel() {
        final String name = getName();
        return isDirty()
                ? "* " + name
                : name;
    }

    /// @return What the tab is called, before any mark that it is dirty: the editor's label for it,
    /// or the document's name.
    protected String getName() {
        final String tabLabel = editor.getTabLabel();
        if (tabLabel != null) {
            return tabLabel;
        } else if (docRef != null) {
            return docRef.getName();
        } else {
            return "";
        }
    }

    /// Called when the document's tab is closed for good, before it is disposed.
    public void onClose() {
        editor.onClose();
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.ofNullable(docRef)
                .map(ref -> type + " - " + ref.getName());
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    /// Shows a task's progress, e.g. a Save As's, on the task monitor its tab provides
    /// ([Contexts#TASK_MONITOR]), or as an app-wide task before it is in a tab.
    ///
    /// @return A task monitor.
    @Override
    public TaskMonitor createTaskMonitor() {
        return taskMonitor().createTaskMonitor();
    }

    private void refreshTabIfLabelChanged() {
        // Only when it changes, as the tab is laid out again
        if (!getLabel().equals(lastLabel)) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }
}
