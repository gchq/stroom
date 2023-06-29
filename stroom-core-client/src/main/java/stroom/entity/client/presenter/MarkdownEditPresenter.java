/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.SandboxOption;
import stroom.preferences.client.UserPreferencesManager;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Collections;
import java.util.List;

public class MarkdownEditPresenter
        extends MyPresenterWidget<MarkdownEditView>
        implements HasDirtyHandlers, HasToolbar {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String MARKDOWN_FRAME_ID = "markdown-frame";

    private final EditorPresenter codePresenter;
    private final IFramePresenter iFramePresenter;
    private final UiConfigCache uiConfigCache;
    private final InlineSvgToggleButton editModeButton;
    private final ButtonView helpButton;
    private final ButtonPanel toolbar;
    private final UserPreferencesManager userPreferencesManager;
    private final MarkdownConverter markdownConverter;
    private boolean reading;
    private boolean readOnly = true;
    private boolean editMode;

    @Inject
    public MarkdownEditPresenter(final EventBus eventBus,
                                 final MarkdownEditView view,
                                 final EditorPresenter editorPresenter,
                                 final IFramePresenter iFramePresenter,
                                 final UiConfigCache uiConfigCache,
                                 final UserPreferencesManager userPreferencesManager,
                                 final MarkdownConverter markdownConverter) {
        super(eventBus, view);
        this.codePresenter = editorPresenter;
        this.iFramePresenter = iFramePresenter;
        this.uiConfigCache = uiConfigCache;
        this.userPreferencesManager = userPreferencesManager;
        this.markdownConverter = markdownConverter;
        codePresenter.setMode(AceEditorMode.MARKDOWN);

        // Markdown is mostly wordy content so wrap long lines to make it easier for the user.
        codePresenter.getLineWrapOption().setOn();

        iFramePresenter.setId(MARKDOWN_FRAME_ID);
        iFramePresenter.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS);
        view.setView(iFramePresenter.getView());

        editModeButton = new InlineSvgToggleButton();
        editModeButton.setSvg(SvgImage.EDIT);
        editModeButton.setTitle("Edit");
        editModeButton.setEnabled(true);

        toolbar = new ButtonPanel();
        toolbar.addButton(editModeButton);
        helpButton = toolbar.addButton(SvgPresets.HELP.title("Documentation help"));

        registerHandler(eventBus.addHandler(ChangeThemeEvent.getType(), event ->
                updateMarkdownOnIFramePresenter()));
    }

    @Override
    public List<Widget> getToolbars() {
        if (readOnly) {
            return Collections.emptyList();
        }
        return Collections.singletonList(toolbar);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(editModeButton.addClickHandler(e -> {
            if (!readOnly) {
                this.editMode = !this.editMode;
                if (editMode) {
                    getView().setView(codePresenter.getView());
                } else {
                    // Raw markdown likely been changed to update the iframe content
                    updateMarkdownOnIFramePresenter();
                    getView().setView(iFramePresenter.getView());
                }
            }
        }));
        registerHandler(helpButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                showHelp();
            }
        }));
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
        return codePresenter.getText();
    }

    public void setText(String rawMarkdown) {
        if (rawMarkdown == null) {
            rawMarkdown = "";
        }

        reading = true;
        codePresenter.setText(rawMarkdown);
        reading = false;
        updateMarkdownOnIFramePresenter();
    }

    private void showHelp() {
        uiConfigCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrlDocumentation();
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        Window.open(helpUrl, "_blank", "");
                    } else {
                        AlertEvent.fireError(
                                MarkdownEditPresenter.this,
                                "Help is not configured!",
                                null);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(
                        MarkdownEditPresenter.this,
                        caught.getMessage(),
                        null));
    }

    private void updateMarkdownOnIFramePresenter() {
        final SafeHtml iFrameHtmlContent = markdownConverter.convertMarkdownToHtmlInFrame(codePresenter.getText());
        iFramePresenter.setSrcDoc(iFrameHtmlContent.asString());
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }


    // --------------------------------------------------------------------------------


    public interface MarkdownEditView extends View {

        void setView(View view);
    }
}
