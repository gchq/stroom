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
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.HasCommonTabs;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.ControllerView;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Opens a [DocumentEditor], or another [TabbedController] such as a folder's tabs, as a tab of
/// Stroom's, for `DocumentPlugin`, which opens, saves and closes documents through presenters.
/// It owns the controller, and:
///
/// * reads the document into it as the plugin reads it (when it is opened, saved, or its lock
///   changes), telling a document editor whether the user may change it;
/// * marks the tab's label while a document editor has changes that aren't saved;
/// * asks the plugin to save the document, or save a copy, when the editor's buttons are pressed.
///
/// ```java
/// @Override
/// protected MyPresenterWidget<?> createEditor() {
///     return new DocumentEditorHost<>(eventBus, editorProvider.get(), DictionaryDoc.TYPE);
/// }
/// ```
///
/// @param <D> The document's type.
public class DocumentEditorHost<D>
        extends DocPresenter<ControllerView, D>
        implements DocumentTabData, HasSave, HasCommonTabs {

    private final TabbedController<D> editor;
    private final String type;
    private DocRef docRef;
    private String lastLabel;

    /// @param eventBus The application's event bus.
    /// @param editor   The document's editor (or a folder's tabs), just made.
    /// @param type     The document's type, e.g. `DictionaryDoc.TYPE`, for the tab's icon.
    public DocumentEditorHost(final EventBus eventBus, final TabbedController<D> editor, final String type) {
        super(eventBus, new ControllerView(editor));
        this.editor = own(editor);
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

    @Override
    protected void onRead(final DocRef docRef, final D document) {
        this.docRef = docRef;
        if (editor instanceof final DocumentEditor<D> documentEditor) {
            documentEditor.setReadOnly(isReadOnly());
        }
        editor.read(docRef, document);
        // The name may have changed (e.g. after a rename or Save As)
        refreshTabIfLabelChanged();
    }

    @Override
    protected D onWrite(final D document) {
        // Into the document given, which for Save As is the new one
        return editor.write(document);
    }

    @Override
    protected void onDirty() {
        refreshTabIfLabelChanged();
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

    @Override
    public BiConsumer<D, Consumer<D>> getPostSaveCallback() {
        return editor instanceof final DocumentEditor<D> documentEditor
                ? documentEditor.getPostSaveCallback()
                : null;
    }

    @Override
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

    @Override
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

    private void refreshTabIfLabelChanged() {
        // Only when it changes, as the tab is laid out again
        if (!getLabel().equals(lastLabel)) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }
}
