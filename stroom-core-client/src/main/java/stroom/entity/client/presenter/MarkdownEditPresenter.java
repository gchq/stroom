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

package stroom.entity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.iframe.client.component.IFrame.SandboxOption;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Listeners;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class MarkdownEditPresenter
        extends MyPresenterWidget<MarkdownEditView>
        implements HasToolbar {

    // Told when the text becomes changed or unchanged
    private final Listeners<Boolean> dirtyListeners = new Listeners<>();

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String MARKDOWN_FRAME_ID = "markdown-frame";

    private final MarkdownPreviewPresenter markdownPreviewPresenter;
    private final IFramePresenter iFramePresenter;
    private final UiConfigCache uiConfigCache;
    private final MarkdownConverter markdownConverter;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode = false;
    // Made when first shown, so that buttons can be added before its own
    private Toolbar toolbar;
    private ToolbarToggleButton editModeButton;
    private Consumer<Toolbar> addButtonsFirst;

    private String lastRawMarkdown = null;
    private SafeHtml lastRenderedMarkdown = null;

    @Inject
    public MarkdownEditPresenter(final EventBus eventBus,
                                 final MarkdownEditView view,
                                 final MarkdownPreviewPresenter markdownPreviewPresenter,
                                 final EditorPresenter editorPresenter,
                                 final IFramePresenter iFramePresenter,
                                 final UiConfigCache uiConfigCache,
                                 final UserPreferencesManager userPreferencesManager,
                                 final MarkdownConverter markdownConverter) {
        super(eventBus, view);
        own(markdownPreviewPresenter);
        own(editorPresenter);
        own(iFramePresenter);
        this.markdownPreviewPresenter = markdownPreviewPresenter;
        this.iFramePresenter = iFramePresenter;
        this.uiConfigCache = uiConfigCache;
        this.markdownConverter = markdownConverter;

        iFramePresenter.setId(MARKDOWN_FRAME_ID);
        iFramePresenter.setAccessibleName("Preview");
        iFramePresenter.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS);
//        view.setView(iFramePresenter.getView());

        listen(ChangeCurrentPreferencesEvent.getType(), event ->
                updateMarkdownOnIFramePresenter());

        own(markdownPreviewPresenter.addValueChangeHandler(event -> setDirty(true)));
        own(markdownPreviewPresenter.addFormatHandler(event -> setDirty(true)));

        setEditMode(false);
    }

    @Override
    public List<Widget> getDocumentButtons() {
        if (readOnly) {
            return Collections.emptyList();
        }
        return Collections.singletonList(getToolbar());
    }

    private Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = new Toolbar("Documentation actions");
            if (addButtonsFirst != null) {
                addButtonsFirst.accept(toolbar);
            }
            editModeButton = toolbar.toggle(SvgImage.EDIT, "Edit Documentation", pressed -> toggleEditMode());
            editModeButton.setPressed(editMode);
            toolbar.button(SvgImage.HELP, "Help with Documentation (opens in new tab)", this::showHelp);
        }
        return toolbar;
    }

    /// Adds buttons to the start of this presenter's toolbar, before its Edit and Help buttons, e.g.
    /// a document's Save button. They are made in the toolbar when it is first shown, so this must
    /// be called before then.
    ///
    /// @param addButtonsFirst Given the toolbar, adds the buttons to it.
    public void setAddButtonsFirst(final Consumer<Toolbar> addButtonsFirst) {
        this.addButtonsFirst = addButtonsFirst;
    }

    private void toggleEditMode() {
        setEditMode(!this.editMode);
    }

    private void setEditMode(final boolean isInEditMode) {
        if (!readOnly && isInEditMode) {
            getView().setView(markdownPreviewPresenter.getView());
            this.editMode = true;
        } else {
            updateMarkdownOnIFramePresenter();
            getView().setView(iFramePresenter.getView());
            this.editMode = false;
        }
        if (editModeButton != null) {
            editModeButton.setPressed(this.editMode);
        }
    }

    /// Listens for the text becoming changed or unchanged, until this presenter is disposed or the
    /// registration removed. A document this is part of is told without a listener.
    ///
    /// @param listener Told whether it is now changed.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addDirtyListener(final Consumer<Boolean> listener) {
        return dirtyListeners.add(listener);
    }

    private void setDirty(final boolean dirty) {
        if (!reading && !readOnly) {
            dirtyListeners.fire(dirty);
            // e.g. the document whose Documentation tab this is
            markChanged();
        }
    }


    public String getText() {
        return markdownPreviewPresenter.getText();
    }

    public void setText(final String rawMarkdown) {
        reading = true;

        final String raw = NullSafe.string(rawMarkdown);
        if (!Objects.equals(markdownPreviewPresenter.getText(), raw)) {
            markdownPreviewPresenter.setText(raw);
        }

        updateEditState();
        reading = false;

        updateMarkdownOnIFramePresenter();
    }

    private void updateEditState() {
        final String rawMarkdown = markdownPreviewPresenter.getText();
        // No content do default to edit mode
        if (NullSafe.isBlankString(rawMarkdown) && !readOnly) {
            GWT.log("setText, editMode: true");
            setEditMode(true);
//            editModeButton.setState(true);
        } else {
            GWT.log("setText, editMode: false");
            setEditMode(false);
//            editModeButton.setState(false);
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
                            MarkdownEditPresenter.this,
                            "Help is not configured!",
                            null);
                }
            }
        }, this);
    }

    private void updateMarkdownOnIFramePresenter() {
        final String rawMarkdown = markdownPreviewPresenter.getText();
        if (!Objects.equals(rawMarkdown, lastRawMarkdown)) {
            lastRawMarkdown = rawMarkdown;
            final SafeHtml iFrameHtmlContent = markdownConverter.convertMarkdownToHtmlInFrame(rawMarkdown);
            if (!Objects.equals(iFrameHtmlContent, lastRenderedMarkdown)) {
                lastRenderedMarkdown = iFrameHtmlContent;
                iFramePresenter.setSrcDoc(iFrameHtmlContent.asString());
//                iFramePresenter.getWidget().getElement().setScrollTop(50);
            }
        }
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        // Ensure we are not in edit mode in case this is called after setText
//        setEditMode(this.editMode);
        updateEditState();
    }

    public interface MarkdownEditView extends View {

        void setView(View view);
    }
}
