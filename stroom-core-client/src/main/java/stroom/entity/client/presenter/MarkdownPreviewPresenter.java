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

import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.MarkdownPreviewPresenter.MarkdownPreviewView;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.SandboxOption;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;

public class MarkdownPreviewPresenter extends MyPresenterWidget<MarkdownPreviewView> {

    // See prismjs-light.css which is a modified version of a prismjs light theme with .stroom-theme-light (the
    // page's theme class) selectivity added into to stop it conflicting with the dark theme.
    private static final String MARKDOWN_FRAME_ID = "markdown-preview-frame";

    private final EditorPresenter codePresenter;
    private final IFramePresenter iFramePresenter;
    private final UiConfigCache uiConfigCache;
    private final UserPreferencesManager userPreferencesManager;
    private final MarkdownConverter markdownConverter;

    private boolean reading;

    private Timer delayedConvertTimer;
    private String lastRawMarkdown = null;
    private SafeHtml lastRenderedMarkdown = null;

    @Inject
    public MarkdownPreviewPresenter(final EventBus eventBus,
                                    final MarkdownPreviewView view,
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

        // Markdown is mostly wordy content so wrap long lines to make it easier for the user.
        codePresenter.getLineWrapOption().setOn();
        codePresenter.setMode(AceEditorMode.MARKDOWN);

        iFramePresenter.setId(MARKDOWN_FRAME_ID);
        // Same origin lets this page set the preview's scroll position, to follow the editor. Scripts are still
        // not allowed, so nothing in the preview can run.
        iFramePresenter.setSandboxEnabled(true, SandboxOption.ALLOW_POPUPS, SandboxOption.ALLOW_SAME_ORIGIN);
        // A link followed in the frame would load Stroom, or another site, in it, where it can't run.
        iFramePresenter.setLinkHandler(href -> MarkdownLinks.open(this, href));

        view.setViews(codePresenter.getView(), iFramePresenter.getView());
        syncScroll(codePresenter.getView().asWidget().getElement(),
                iFramePresenter.getView().asWidget().getElement());

        delayedConvertTimer = new Timer() {
            @Override
            public void run() {
                updateMarkdownOnIFramePresenter();
            }
        };
    }

    public String getText() {
        return codePresenter.getText();
    }

    public void setText(String rawMarkdown) {
        if (rawMarkdown == null) {
            rawMarkdown = "";
        }

        reading = true;
        if (!codePresenter.getText().equals(rawMarkdown)) {
            codePresenter.setText(rawMarkdown);
        }
        reading = false;
        updateMarkdownOnIFramePresenter();
    }

    private void updateMarkdownOnIFramePresenter() {
        final String rawMarkdown = codePresenter.getText();
        if (!Objects.equals(rawMarkdown, lastRawMarkdown)) {
            lastRawMarkdown = rawMarkdown;
            final SafeHtml iFrameHtmlContent = markdownConverter.convertMarkdownToHtmlInFrame(rawMarkdown);
            if (!Objects.equals(iFrameHtmlContent, lastRenderedMarkdown)) {
                lastRenderedMarkdown = iFrameHtmlContent;
                iFramePresenter.setSrcDoc(iFrameHtmlContent.asString());
                iFramePresenter.getWidget().getElement().setScrollTop(50);
            }
        }
    }

    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return codePresenter.addValueChangeHandler(event -> {
            // Each time the user changes something restart the timer so that if they are furiously typing they
            // are not interrupted by it updating all the time.  They just need to pause for 1s to let the update
            // happen
            delayedConvertTimer.cancel();
            delayedConvertTimer.schedule(1_000);
            handler.onValueChange(event);
        });
    }

    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return codePresenter.addFormatHandler(handler);
    }

    /**
     * Keeps the preview scrolled to the same place as the editor, as a proportion of the way down, including
     * after the preview is re-rendered. The Ace editor scrolls a real scroll bar element, whose scroll events
     * (which do not bubble) are caught on the way down to it.
     *
     * @param editorElement  The editor's element.
     * @param previewElement The preview's element: its iframe, or an element containing it.
     */
    private static native void syncScroll(Element editorElement, Element previewElement) /*-{
        var fraction = 0;
        var apply = function () {
            var frame = previewElement.tagName === 'IFRAME'
                ? previewElement
                : previewElement.querySelector('iframe');
            var doc = null;
            try {
                doc = frame ? frame.contentDocument : null;
            } catch (e) {
                // Not accessible, so the preview cannot follow.
            }
            if (!doc) {
                return;
            }
            // The rendered document scrolls inside its container, rather than the page itself scrolling.
            var candidates = [doc.querySelector('.markdown-container'), doc.scrollingElement, doc.documentElement];
            for (var i = 0; i < candidates.length; i++) {
                var scroller = candidates[i];
                if (scroller && scroller.scrollHeight > scroller.clientHeight) {
                    // Instantly, as the container scrolls smoothly, which would lag behind the editor.
                    scroller.scrollTo({
                        top: fraction * (scroller.scrollHeight - scroller.clientHeight),
                        behavior: 'instant'
                    });
                    return;
                }
            }
        };
        editorElement.addEventListener('scroll', function (event) {
            var bar = event.target;
            if (bar && bar.classList && bar.classList.contains('ace_scrollbar-v')) {
                var max = bar.scrollHeight - bar.clientHeight;
                fraction = max > 0 ? bar.scrollTop / max : 0;
                apply();
            }
        }, true);
        // A re-rendered preview starts at the top, so put it back where the editor is.
        previewElement.addEventListener('load', apply, true);
    }-*/;

    // --------------------------------------------------------------------------------


    public interface MarkdownPreviewView extends View {

        void setViews(final View editorView, final View previewView);

    }
}
