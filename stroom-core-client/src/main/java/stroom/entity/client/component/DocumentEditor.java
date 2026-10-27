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

import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.DirtyTracker;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/// A document's editor: its tabs (see [TabbedController]), its toolbar with Save and Save As,
/// whether it is read only, and whether it has changes that aren't saved. A document type's editor
/// adds its tabs (`addTab`) and any buttons of its own ([#getToolbar()]).
///
/// Its owner reads the document in ([#read]), says whether the user may change it
/// ([#setReadOnly(boolean)]), which every tab follows through [Contexts#READ_ONLY], and saves it
/// when asked ([#setOnSave(Runnable)], [#write()]). In Stroom the owner is a
/// [DocumentEditorHost], as `DocumentPlugin` opens it. Tabs tell the editor of changes through
/// [#onChange()], and the editor works out whether the document is dirty by comparing it with what
/// the tabs write.
///
/// @param <D> The document's type, which must compare by value (`equals`).
public abstract class DocumentEditor<D> extends TabbedController<D> {

    private final ContextValue<Boolean> readOnly = ContextValue.of(true);
    private final ToolbarButton saveButton;
    private final ToolbarButton saveAsButton;

    private boolean dirty;
    private Consumer<Boolean> onDirtyChange = isDirty -> {
    };
    private Runnable onSave = () -> {
    };
    private Runnable onSaveAs = () -> {
    };

    /// @param eventBus The application's event bus.
    /// @param view     The tab bar, toolbar row and tab content.
    protected DocumentEditor(final EventBus eventBus, final LinkTabPanelView view) {
        super(eventBus, view);
        provide(Contexts.READ_ONLY, readOnly);
        // Its tabs, however deep, may tell it of changes this way (markChanged) rather than
        // through a callback
        provide(Contexts.DIRTY, ContextValue.<DirtyTracker>of(this::onChange));

        saveButton = getToolbar().button(SvgImage.SAVE, "Save", this::save);
        // Its tooltip shows the save shortcut, as a document tab's does
        saveButton.setShortcut(Action.ITEM_SAVE);
        saveButton.setEnabled(false);
        saveAsButton = getToolbar().button(SvgImage.SAVEAS, "Save As", this::saveAs);
        saveAsButton.setEnabled(false);

        showReadOnlyStatus();
        watch(Contexts.READ_ONLY, readOnly -> saveButton.setTitle(readOnly
                ? "Save (read only)"
                : "Save"));
    }

    /// @param readOnly Whether the user may not change the document. Every tab follows it.
    public final void setReadOnly(final boolean readOnly) {
        this.readOnly.set(readOnly);
    }

    /// @return Whether the tabs show changes that aren't saved.
    public final boolean isDirty() {
        return dirty;
    }

    /// @param onDirtyChange What to run when the document becomes dirty or clean, e.g. to mark
    ///                      its tab's label.
    public final void setOnDirtyChange(final Consumer<Boolean> onDirtyChange) {
        this.onDirtyChange = Objects.requireNonNull(onDirtyChange, "onDirtyChange");
    }

    /// @param onSave What to run when the user asks to save the document.
    public final void setOnSave(final Runnable onSave) {
        this.onSave = Objects.requireNonNull(onSave, "onSave");
    }

    /// @param onSaveAs What to run when the user asks to save a copy of the document.
    public final void setOnSaveAs(final Runnable onSaveAs) {
        this.onSaveAs = Objects.requireNonNull(onSaveAs, "onSaveAs");
    }

    /// Adds the document's Documentation tab, its description in Markdown, which a link to the
    /// document's documentation opens.
    ///
    /// @param tab             The tab.
    /// @param provider        Makes the Markdown editor.
    /// @param description     Gets the description from the document.
    /// @param withDescription Makes a copy of the document with a description.
    protected final void addDocumentationTab(final TabData tab,
                                             final Provider<MarkdownEditController> provider,
                                             final Function<D, String> description,
                                             final BiFunction<D, String, D> withDescription) {
        addTab(tab,
                () -> {
                    // It tells the editor of changes itself (Contexts.DIRTY)
                    final MarkdownEditController documentation = provider.get();
                    documentation.followReadOnly();
                    return documentation;
                },
                (documentation, docRef, doc) -> documentation.setText(description.apply(doc)),
                (documentation, doc) -> withDescription.apply(doc, documentation.getText()));
        setCommonTab(CommonDocLinkTab.DOCUMENTATION, tab);
    }

    /// Adds the document's Permissions tab, which a link to the document's permissions opens.
    ///
    /// @param tab      The tab.
    /// @param provider Makes the permissions tab.
    protected final void addPermissionsTab(final TabData tab,
                                           final Provider<DocumentUserPermissionsTabController> provider) {
        addTab(tab, provider::get, (permissions, docRef, doc) -> permissions.setDocRef(docRef));
        setCommonTab(CommonDocLinkTab.PERMISSIONS, tab);
    }

    /// Called by a tab's content when the user changes what it shows, so the editor can tell
    /// whether the document is dirty.
    protected final void onChange() {
        if (getDocument() != null && !context(Contexts.READ_ONLY)) {
            // What the document has but doesn't hold first, as comparing the document, on every
            // key press in an editor, may cost more
            setDirty(hasAssociatedDirty() || !Objects.equals(getDocument(), write()));
        }
    }

    /// Whether something that goes with the document but isn't held in it (e.g. its assets, saved
    /// after it) has changes that aren't saved.
    ///
    /// @return False by default.
    protected boolean hasAssociatedDirty() {
        return false;
    }

    /// @return What to run once the document is saved, before it is read again, e.g. to save its
    /// assets: given the saved document and what to run when it is done; or null for nothing.
    public BiConsumer<D, Consumer<D>> getPostSaveCallback() {
        return null;
    }

    /// @return What to run once a copy of the document is saved (Save As), before the copy is read,
    /// as [#getPostSaveCallback()]; or null for nothing.
    public BiConsumer<D, Consumer<D>> getPostSaveAsCallback() {
        return null;
    }

    @Override
    void afterRead() {
        saveAsButton.setEnabled(true);
        setDirty(false);
    }

    private void setDirty(final boolean dirty) {
        saveButton.setEnabled(dirty);
        if (this.dirty != dirty) {
            this.dirty = dirty;
            onDirtyChange.accept(dirty);
        }
    }

    /// Saves the document if it has changes, as Save does: once [#beforeSave(Runnable)] lets it,
    /// the owner saves it ([#setOnSave(Runnable)]).
    public final void save() {
        if (dirty) {
            beforeSave(onSave);
        }
    }

    /// Called when the user asks to save the document, e.g. to ask them to confirm first.
    ///
    /// @param save Saves the document; by default it is run straight away.
    protected void beforeSave(final Runnable save) {
        save.run();
    }

    private void saveAs() {
        if (getDocument() != null) {
            onSaveAs.run();
        }
    }
}
