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

package stroom.entity.client.component;

import stroom.editor.client.ChangeCurrentPreferencesEvent;
import stroom.editor.client.component.CodeEditorController;
import stroom.entity.client.MarkdownConverter;
import stroom.iframe.client.component.IFrame;
import stroom.iframe.client.component.IFrame.SandboxOption;
import stroom.widget.component.client.Controller;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;

/// Markdown being edited beside its preview ([MarkdownEditor]), attached to one its owner
/// declares. The preview is rendered again a second after the user stops typing.
public class MarkdownEditorController extends Controller {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-light-theme
    // selectivity added into to stop it conflicting with the dark theme.
    private static final String MARKDOWN_PREVIEW_FRAME_ID = "markdown-preview-frame";

    private final Provider<CodeEditorController> codeEditorControllerProvider;
    private final MarkdownConverter markdownConverter;
    private final Timer delayedConvertTimer;

    private CodeEditorController editor;
    private IFrame preview;
    private String lastRawMarkdown = null;
    private SafeHtml lastRenderedMarkdown = null;
    private Runnable onChange = () -> {
    };

    /// Made by GIN; [#attach(MarkdownEditor)] gives it its editor and preview.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param codeEditorControllerProvider Makes the code editor's controller.
    /// @param markdownConverter            Renders the Markdown.
    @Inject
    public MarkdownEditorController(final EventBus eventBus,
                                    final Provider<CodeEditorController> codeEditorControllerProvider,
                                    final MarkdownConverter markdownConverter) {
        super(eventBus);
        this.codeEditorControllerProvider = codeEditorControllerProvider;
        this.markdownConverter = markdownConverter;
        delayedConvertTimer = new Timer() {
            @Override
            public void run() {
                updatePreview();
            }
        };
        onDispose(delayedConvertTimer::cancel);
    }

    /// Attaches this to the editor and preview it fills, e.g. one a template declares.
    ///
    /// @param view The editor and preview.
    /// @return This, for its owner to keep.
    public MarkdownEditorController attach(final MarkdownEditor view) {
        Objects.requireNonNull(view, "view");
        editor = make(codeEditorControllerProvider).attach(view.getEditor());
        // Markdown is mostly wordy content so wrap long lines to make it easier for the user.
        editor.getOptions().getLineWrapOption().setOn();
        editor.setMode(AceEditorMode.MARKDOWN);
        own(editor.addValueChangeHandler(event -> {
            // Each time the user changes something restart the timer so that if they are furiously typing they
            // are not interrupted by it updating all the time.  They just need to pause for 1s to let the update
            // happen
            delayedConvertTimer.cancel();
            delayedConvertTimer.schedule(1_000);
            onChange.run();
        }));
        own(editor.addFormatListener(() -> onChange.run()));

        preview = view.getPreview();
        preview.setId(MARKDOWN_PREVIEW_FRAME_ID);
        preview.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS);
        // A new theme renders the same Markdown differently, so render it again
        listen(ChangeCurrentPreferencesEvent.getType(), event -> {
            lastRawMarkdown = null;
            updatePreview();
        });
        return this;
    }

    /// @return The Markdown entered.
    public String getText() {
        return editor.getText();
    }

    /// Shows Markdown and its preview.
    ///
    /// @param rawMarkdown The Markdown, or null for none.
    public void setText(final String rawMarkdown) {
        final String text = rawMarkdown == null
                ? ""
                : rawMarkdown;
        if (!editor.getText().equals(text)) {
            editor.setText(text);
        }
        updatePreview();
    }

    /// @param onChange What to run when the user changes or formats the Markdown.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void updatePreview() {
        final String rawMarkdown = editor.getText();
        if (!Objects.equals(rawMarkdown, lastRawMarkdown)) {
            lastRawMarkdown = rawMarkdown;
            final SafeHtml iFrameHtmlContent = markdownConverter.convertMarkdownToHtmlInFrame(rawMarkdown);
            if (!Objects.equals(iFrameHtmlContent, lastRenderedMarkdown)) {
                lastRenderedMarkdown = iFrameHtmlContent;
                preview.setSrcDoc(iFrameHtmlContent.asString());
                preview.getElement().setScrollTop(50);
            }
        }
    }
}
