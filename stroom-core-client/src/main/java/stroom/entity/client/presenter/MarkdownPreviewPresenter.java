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

    /// @return The element of the frame showing the rendered markdown.
    public Element getPreviewElement() {
        return iFramePresenter.getView().asWidget().getElement();
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
     * Keeps the preview in step with the editor, including after the preview is re-rendered: at the place the
     * cursor's line is shown, when the cursor moves (asked for by the user), else scrolled the same proportion of
     * the way down as the editor. The Ace editor scrolls a real scroll bar element, whose scroll events (which do
     * not bubble) are caught on the way down to it.
     *
     * @param editorElement  The editor's element.
     * @param previewElement The preview's element: its iframe, or an element containing it.
     */
    private static native void syncScroll(Element editorElement, Element previewElement) /*-{
        var fraction = 0;
        // The cursor's line, when the preview last followed the cursor rather than the editor's scrolling.
        var cursorRow = -1;
        var cursorMovedMs = 0;
        // A scroll this soon after the cursor moved is the editor bringing the cursor into view: the preview
        // follows the cursor, not that.
        var cursorWinsMs = 400;
        var frameDocument = function () {
            var frame = previewElement.tagName === 'IFRAME'
                ? previewElement
                : previewElement.querySelector('iframe');
            try {
                return frame ? frame.contentDocument : null;
            } catch (e) {
                // Not accessible, so the preview cannot follow.
                return null;
            }
        };
        // The rendered document scrolls inside its container, rather than the page itself scrolling.
        var scrollerOf = function (doc) {
            var candidates = [doc.querySelector('.markdown-container'), doc.scrollingElement, doc.documentElement];
            for (var i = 0; i < candidates.length; i++) {
                var scroller = candidates[i];
                if (scroller && scroller.scrollHeight > scroller.clientHeight) {
                    return scroller;
                }
            }
            return null;
        };
        var aceEditor = function () {
            var ace = editorElement.querySelector('.ace_editor');
            return ace && ace.env ? ace.env.editor : null;
        };
        // The top-level headings' lines in the markdown, as the preview's sections are made from them (see
        // MarkdownConverter): ATX headings and underlined ones, none in a code fence.
        var headingLines = function (lines) {
            var found = [];
            var fence = null;
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
                var opens = /^ {0,3}(`{3,}|~{3,})/.exec(line);
                if (fence) {
                    if (opens && opens[1].charAt(0) === fence.charAt(0) && opens[1].length >= fence.length
                        && /^ {0,3}(`{3,}|~{3,})\s*$/.test(line)) {
                        fence = null;
                    }
                    continue;
                }
                if (opens) {
                    fence = opens[1];
                    continue;
                }
                var underlined = i + 1 < lines.length
                                 && (/^ {0,3}=+\s*$/.test(lines[i + 1]) || /^ {0,3}-+\s*$/.test(lines[i + 1]));
                // An underlined heading is one line of text after a blank line (or a heading), not a list item or
                // a quote; else a line of dashes is a rule.
                var startsBlock = i === 0 || !/\S/.test(lines[i - 1]) || found[found.length - 1] === i - 1;
                if (/^ {0,3}#{1,6}(\s|$)/.test(line)) {
                    found.push(i);
                } else if (underlined && startsBlock && /\S/.test(line) && !/^\s*([-*+>]|\d+[.)])\s/.test(line)) {
                    found.push(i);
                    // Its underline is not a line of text.
                    i++;
                }
            }
            return found;
        };
        // Where an element is shown, in the scroller's content: for a heading in a closed section, the heading of
        // the outermost closed section it is in, which is what shows.
        var topOf = function (element, scroller, doc) {
            var shown = element;
            for (var d = element.closest('details'); d; d = d.parentElement.closest('details')) {
                if (!d.open) {
                    shown = d.firstElementChild || d;
                }
            }
            var page = scroller === doc.scrollingElement || scroller === doc.documentElement;
            return shown.getBoundingClientRect().top + scroller.scrollTop
                   - (page ? 0 : scroller.getBoundingClientRect().top);
        };
        var followCursor = function () {
            var doc = frameDocument();
            var scroller = doc ? scrollerOf(doc) : null;
            var editor = aceEditor();
            if (!scroller || !editor || cursorRow < 0) {
                return false;
            }
            var lines = editor.getValue().split('\n');
            var sources = headingLines(lines);
            var shown = Array.prototype.slice.call(doc.querySelectorAll(
                '.markdown-section__heading > :is(h1, h2, h3, h4, h5, h6)'));
            var count = Math.min(sources.length, shown.length);
            var target;
            if (count === 0) {
                target = (lines.length > 1 ? cursorRow / (lines.length - 1) : 0) * scroller.scrollHeight;
            } else {
                // The section the cursor is in (or the text before the first heading), and how far into it.
                var k = -1;
                while (k + 1 < count && sources[k + 1] <= cursorRow) {
                    k++;
                }
                var fromLine = k < 0 ? 0 : sources[k];
                var toLine = k + 1 < count ? sources[k + 1] : lines.length;
                var fromTop = k < 0 ? 0 : topOf(shown[k], scroller, doc);
                var toTop = k + 1 < count ? topOf(shown[k + 1], scroller, doc) : scroller.scrollHeight;
                var into = toLine > fromLine ? (cursorRow - fromLine) / (toLine - fromLine) : 0;
                target = fromTop + into * Math.max(0, toTop - fromTop);
            }
            // A third of the way down, below any headings pinned at the top.
            scroller.scrollTo({top: Math.max(0, target - scroller.clientHeight / 3), behavior: 'instant'});
            return true;
        };
        var followScroll = function () {
            var doc = frameDocument();
            var scroller = doc ? scrollerOf(doc) : null;
            if (scroller) {
                // Instantly, as the container scrolls smoothly, which would lag behind the editor.
                scroller.scrollTo({
                    top: fraction * (scroller.scrollHeight - scroller.clientHeight),
                    behavior: 'instant'
                });
            }
        };
        var onCursor = function () {
            var editor = aceEditor();
            if (!editor) {
                return;
            }
            cursorRow = editor.getCursorPosition().row;
            cursorMovedMs = Date.now();
            followCursor();
        };
        // The editor is made when it is first shown, so its cursor is listened to once it is.
        var listening = null;
        var listen = function () {
            var editor = aceEditor();
            if (editor && editor !== listening) {
                listening = editor;
                editor.selection.on('changeCursor', onCursor);
            }
        };
        editorElement.addEventListener('focusin', listen, true);
        editorElement.addEventListener('mousedown', listen, true);
        editorElement.addEventListener('scroll', function (event) {
            var bar = event.target;
            if (bar && bar.classList && bar.classList.contains('ace_scrollbar-v')) {
                var max = bar.scrollHeight - bar.clientHeight;
                fraction = max > 0 ? bar.scrollTop / max : 0;
                if (Date.now() - cursorMovedMs > cursorWinsMs) {
                    cursorRow = -1;
                    followScroll();
                }
            }
        }, true);
        // A re-rendered preview starts at the top, so put it back where the cursor, or the editor, is.
        previewElement.addEventListener('load', function () {
            if (!followCursor()) {
                followScroll();
            }
        }, true);
    }-*/;

    // --------------------------------------------------------------------------------


    public interface MarkdownPreviewView extends View {

        void setViews(final View editorView, final View previewView);

    }
}
