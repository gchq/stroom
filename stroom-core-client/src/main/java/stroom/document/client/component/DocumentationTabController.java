/*
 * Copyright 2023 Crown Copyright
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

package stroom.document.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.MarkdownConverter;
import stroom.document.client.editor.HasToolbar;
import stroom.iframe.client.component.IFrame;
import stroom.iframe.client.component.IFrame.SandboxOption;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Listeners;
import stroom.widget.preferences.client.ChangeCurrentPreferencesEvent;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A document's documentation ([DocumentationTab]): shows its rendered Markdown, and lets the user
/// edit it with Edit Documentation, a button it adds to the document's toolbar.
public class DocumentationTabController extends Controller implements HasToolbar {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String MARKDOWN_FRAME_ID = "markdown-frame";

    // Told when the text becomes changed or unchanged
    private final Listeners<Boolean> dirtyListeners = new Listeners<>();

    private final DocumentationTab view;
    private final MarkdownEditorController editor;
    private final IFrame preview;
    private final UiConfigCache uiConfigCache;
    private final MarkdownConverter markdownConverter;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode = false;
    // Set once the documentation has first been shown, after which reading it again (e.g. after
    // Save) leaves editing as it is
    private boolean shown;
    // Made when first shown, so that buttons can be added before its own
    private Toolbar toolbar;
    private ToolbarToggleButton editModeButton;
    private Consumer<Toolbar> addButtonsFirst;

    private String lastRawMarkdown = null;
    private SafeHtml lastRenderedMarkdown = null;

    /// Made by GIN, with its documentation.
    ///
    /// @param eventBus                The application's event bus.
    /// @param view                    The documentation.
    /// @param markdownEditorProvider Makes the Markdown editor's controller.
    /// @param uiConfigCache           Gives the address of the help.
    /// @param markdownConverter       Renders the Markdown.
    @Inject
    public DocumentationTabController(final EventBus eventBus,
                                      final DocumentationTab view,
                                      final Provider<MarkdownEditorController> markdownEditorProvider,
                                      final UiConfigCache uiConfigCache,
                                      final MarkdownConverter markdownConverter) {
        super(eventBus);
        this.view = view;
        this.uiConfigCache = uiConfigCache;
        this.markdownConverter = markdownConverter;
        initWidget(view);
        editor = make(markdownEditorProvider).attach(view.getEditor());
        editor.setOnChange(() -> setDirty(true));
        preview = view.getPreview();
        preview.setId(MARKDOWN_FRAME_ID);
        preview.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS);

        // A new theme renders the same Markdown differently, so render it again
        listen(ChangeCurrentPreferencesEvent.getType(), event -> {
            lastRawMarkdown = null;
            updatePreview();
        });

        setEditMode(false);
    }

    @Override
    public List<Widget> getDocumentButtons() {
        if (readOnly) {
            return Collections.emptyList();
        }
        return Collections.singletonList(getToolbar());
    }

    /// Adds buttons to the start of this one's toolbar, before its Edit and Help buttons, e.g.
    /// a document's Save button. They are made in the toolbar when it is first shown, so this must
    /// be called before then.
    ///
    /// @param addButtonsFirst Given the toolbar, adds the buttons to it.
    public void setAddButtonsFirst(final Consumer<Toolbar> addButtonsFirst) {
        this.addButtonsFirst = addButtonsFirst;
    }

    /// Listens for the text becoming changed or unchanged, until this is disposed or the
    /// registration removed. A document this is part of is told without a listener.
    ///
    /// @param listener Told whether it is now changed.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addDirtyListener(final Consumer<Boolean> listener) {
        return dirtyListeners.add(listener);
    }

    /// @return The Markdown.
    public String getText() {
        return editor.getText();
    }

    /// Shows Markdown. The first time, it is rendered, or shown in the editor if there is none and it
    /// may be changed; after that (e.g. after Save) the editor stays open or closed as it was.
    ///
    /// @param rawMarkdown The Markdown, or null for none.
    public void setText(final String rawMarkdown) {
        reading = true;

        final String raw = NullSafe.string(rawMarkdown);
        if (!Objects.equals(editor.getText(), raw)) {
            editor.setText(raw);
        }

        if (!shown) {
            shown = true;
            updateEditState();
        }
        reading = false;

        updatePreview();
    }

    /// Sets whether the documentation may be changed, before or after it is read; read only, it
    /// leaves editing for the preview, and once it may be changed, documentation with no text opens
    /// in the editor. Otherwise editing stays as it is. Its owner calls it, as it isn't followed from
    /// [Contexts#READ_ONLY] (true where nothing gives it) unless asked ([#followReadOnly()]).
    ///
    /// @param readOnly True if it may not be changed.
    public void setReadOnly(final boolean readOnly) {
        final boolean changed = this.readOnly != readOnly;
        this.readOnly = readOnly;
        if (readOnly) {
            setEditMode(false);
        } else if (changed && NullSafe.isBlankString(editor.getText())) {
            setEditMode(true);
        }
    }

    /// Follows whether its owner may be changed ([Contexts#READ_ONLY]), for a tab of a document
    /// editor, which gives it.
    public void followReadOnly() {
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    private Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = new Toolbar("Documentation actions");
            if (addButtonsFirst != null) {
                addButtonsFirst.accept(toolbar);
            }
            editModeButton = toolbar.toggle(SvgImage.EDIT, "Edit Documentation", pressed -> toggleEditMode());
            editModeButton.setPressed(editMode);
            toolbar.button(SvgImage.HELP, "Help with Documentation (opens in new tab)", "Help", this::showHelp);
        }
        return toolbar;
    }

    private void toggleEditMode() {
        setEditMode(!this.editMode);
    }

    private void setEditMode(final boolean isInEditMode) {
        if (!readOnly && isInEditMode) {
            view.setEditing(true);
            this.editMode = true;
        } else {
            updatePreview();
            view.setEditing(false);
            this.editMode = false;
        }
        if (editModeButton != null) {
            editModeButton.setPressed(this.editMode);
        }
    }

    private void setDirty(final boolean dirty) {
        if (!reading && !readOnly) {
            dirtyListeners.fire(dirty);
            // e.g. the document whose Documentation tab this is
            markChanged();
        }
    }

    private void updateEditState() {
        final String rawMarkdown = editor.getText();
        // Run when the documentation is first shown: edit if there is no content and it may be
        // changed, otherwise show the preview
        if (NullSafe.isBlankString(rawMarkdown) && !readOnly) {
            setEditMode(true);
        } else {
            setEditMode(false);
        }
    }

    private void showHelp() {
        uiConfigCache.get(result -> {
            if (result != null) {
                final String helpUrl = result.getHelpUrlDocumentation();
                if (!NullSafe.isBlankString(helpUrl)) {
                    Window.open(helpUrl, "_blank", "");
                } else {
                    AlertEvent.fireError(
                            DocumentationTabController.this,
                            "Help is not configured!",
                            null);
                }
            }
        }, taskMonitor());
    }


    private void updatePreview() {
        final String rawMarkdown = editor.getText();
        if (!Objects.equals(rawMarkdown, lastRawMarkdown)) {
            lastRawMarkdown = rawMarkdown;
            final SafeHtml iFrameHtmlContent = markdownConverter.convertMarkdownToHtmlInFrame(rawMarkdown);
            if (!Objects.equals(iFrameHtmlContent, lastRenderedMarkdown)) {
                lastRenderedMarkdown = iFrameHtmlContent;
                preview.setSrcDoc(iFrameHtmlContent.asString());
            }
        }
    }
}
