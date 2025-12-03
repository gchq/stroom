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

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.SandboxOption;
import stroom.preferences.client.UserPreferencesManager;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.button.client.SvgButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MarkdownEditPresenter
        extends MyPresenterWidget<MarkdownEditView>
        implements HasDirtyHandlers, HasToolbar {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String MARKDOWN_FRAME_ID = "markdown-frame";

    private final MarkdownPreviewPresenter markdownPreviewPresenter;
    private final IFramePresenter iFramePresenter;
    private final UiConfigCache uiConfigCache;
    private final InlineSvgToggleButton editModeButton;
    private final ButtonView helpButton;
    private final MarkdownConverter markdownConverter;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode = false;
    private List<ButtonView> insertedButtons;

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
        this.markdownPreviewPresenter = markdownPreviewPresenter;
        this.iFramePresenter = iFramePresenter;
        this.uiConfigCache = uiConfigCache;
        this.markdownConverter = markdownConverter;

        iFramePresenter.setId(MARKDOWN_FRAME_ID);
        iFramePresenter.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS);
//        view.setView(iFramePresenter.getView());

        editModeButton = new InlineSvgToggleButton();
        editModeButton.setSvg(SvgImage.EDIT);
        editModeButton.setTitle("Edit");
        editModeButton.setEnabled(true);
        helpButton = SvgButton.create(SvgPresets.HELP.title("Documentation help"));

        registerHandler(eventBus.addHandler(ChangeCurrentPreferencesEvent.getType(), event ->
                updateMarkdownOnIFramePresenter()));
    }

    @Override
    public List<Widget> getToolbars() {
        if (readOnly) {
            return Collections.emptyList();
        }
        return Collections.singletonList(createToolbar());
    }

    private ButtonPanel createToolbar() {
        final ButtonPanel toolbar = new ButtonPanel();
        if (insertedButtons != null) {
            toolbar.addButtons(insertedButtons);
        }
        toolbar.addButton(editModeButton);
        toolbar.addButton(helpButton);
        return toolbar;
    }

    public void setInsertedButtons(final List<ButtonView> insertedButtons) {
        this.insertedButtons = insertedButtons;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(markdownPreviewPresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(markdownPreviewPresenter.addFormatHandler(event -> setDirty(true)));

        registerHandler(editModeButton.addClickHandler(e -> {
            toggleEditMode();
        }));
        registerHandler(helpButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                showHelp();
            }
        }));
        setEditMode(false);
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
        editModeButton.setState(this.editMode);
    }

    private void setDirty(final boolean dirty) {
        if (!reading && !readOnly) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public String getText() {
        return markdownPreviewPresenter.getText();
    }

    public void setText(String rawMarkdown) {
        if (rawMarkdown == null) {
            rawMarkdown = "";
        }

        reading = true;

        if (!Objects.equals(markdownPreviewPresenter.getText(), rawMarkdown)) {
            markdownPreviewPresenter.setText(rawMarkdown);
        }

        updateEditState();

//        // No content do default to edit mode
//        if (NullSafe.isBlankString(rawMarkdown)) {
//            GWT.log("setText, editMode: true");
//            setEditMode(true);
////            editModeButton.setState(true);
//        } else {
//            GWT.log("setText, editMode: false");
//            setEditMode(false);
////            editModeButton.setState(false);
//        }
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
        final SafeHtml iFrameHtmlContent = markdownConverter.convertMarkdownToHtmlInFrame(
                markdownPreviewPresenter.getText());
        iFramePresenter.setSrcDoc(iFrameHtmlContent.asString());
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        // Ensure we are not in edit mode in case this is called after setText
//        setEditMode(this.editMode);
        updateEditState();
    }

    // --------------------------------------------------------------------------------


    public interface MarkdownEditView extends View {

        void setView(View view);
    }
}
