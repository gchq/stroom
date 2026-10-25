/*
 * Copyright 2018 Crown Copyright
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

package stroom.iframe.client.view;

import stroom.iframe.client.presenter.IFrameLoadUiHandlers;
import stroom.iframe.client.presenter.IFramePresenter.IFrameView;
import stroom.iframe.client.presenter.IFramePresenter.SandboxOption;
import stroom.util.client.ClipboardUtil;
import stroom.util.client.CopiedFlash;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class IFrameViewImpl extends ViewWithUiHandlers<IFrameLoadUiHandlers> implements IFrameView {

    private static final String DEFAULT_TITLE = "Loading...";
    private static final String SOURCE_DOC_ATTRIBUTE = "srcdoc";
    private static final String SANDBOX_ATTRIBUTE = "sandbox";

    private final SimplePanel panel = new SimplePanel();
    private final Frame frame = new Frame();
    private boolean updateTitle = true;
    private String lastTitle = DEFAULT_TITLE;
    private String customTitle;

    @Inject
    public IFrameViewImpl() {
        panel.getElement().addClassName("iframe-panel");
        frame.getElement().addClassName("iframe-frame");

        panel.setWidget(frame);
        // Each time a document loads into the frame, pass its key presses and link clicks on, if this page can see
        // into it.
        frame.addLoadHandler(event -> {
            forwardKeyDowns(frame.getElement());
            forwardLinkClicks(frame.getElement());
        });

        final RepeatingCommand updateTitleCommand = () -> {
            final IFrameElement iFrameElement = frame.getElement().cast();
            try {
                final String title = iFrameElement.getContentDocument().getTitle();
                if (title != null && title.trim().length() > 0) {
                    if (!Objects.equals(lastTitle, title)) {
                        lastTitle = title;
                        getUiHandlers().onTitleChange(title);
                    }
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
            return updateTitle && customTitle == null;
        };
        Scheduler.get().scheduleFixedDelay(updateTitleCommand, 1000);
    }

    private void onFrameKeyDown(final NativeEvent nativeEvent) {
        if (getUiHandlers() != null) {
            getUiHandlers().onFrameKeyDown(nativeEvent);
        }
    }

    /**
     * Passes key presses in the frame's document to {@link #onFrameKeyDown(NativeEvent)}. Does nothing for a
     * document from another origin, or in a sandbox without 'allow-same-origin', as this page cannot see into
     * it.
     */
    private native void forwardKeyDowns(Element frameElement) /*-{
        var self = this;
        var doc = null;
        try {
            doc = frameElement.contentDocument;
        } catch (e) {
            // Another origin.
        }
        if (doc) {
            doc.addEventListener('keydown', function (event) {
                self.@stroom.iframe.client.view.IFrameViewImpl::onFrameKeyDown(*)(event);
            });
        }
    }-*/;

    /// A rendered markdown table as markdown again (asked for by the user): a header row, the alignment of each
    /// column, then its rows; in a cell, code, links, bold and italic as markdown, a pipe escaped and a line break
    /// as `<br>`.
    ///
    /// @param table The table's element.
    /// @return Its markdown.
    private static native String tableMarkdown(Element table) /*-{
        var inline = function (node) {
            if (node.nodeType === 3) {
                return node.nodeValue.replace(/\s+/g, ' ');
            }
            if (node.nodeType !== 1) {
                return '';
            }
            var inner = '';
            for (var i = 0; i < node.childNodes.length; i++) {
                inner += inline(node.childNodes[i]);
            }
            switch (node.tagName) {
                case 'CODE':
                    var text = node.textContent;
                    var ticks = /`/.test(text) ? '``' : '`';
                    return ticks + (ticks.length > 1 ? ' ' + text + ' ' : text) + ticks;
                case 'A':
                    return '[' + inner + '](' + node.getAttribute('href') + ')';
                case 'STRONG':
                case 'B':
                    return '**' + inner + '**';
                case 'EM':
                case 'I':
                    return '_' + inner + '_';
                case 'BR':
                    return '<br>';
                default:
                    return inner;
            }
        };
        var cell = function (element) {
            return inline(element).trim().replace(/\|/g, '\\|');
        };
        var line = function (cells) {
            return '| ' + cells.join(' | ') + ' |';
        };
        var rows = Array.prototype.slice.call(table.rows);
        if (rows.length === 0) {
            return '';
        }
        var head = Array.prototype.slice.call(rows[0].cells);
        var lines = [line(head.map(cell))];
        lines.push(line(head.map(function (th) {
            var align = (th.getAttribute('align') || th.style.textAlign || '').toLowerCase();
            return align === 'center'
                ? ':---:'
                : align === 'right'
                    ? '---:'
                    : align === 'left'
                        ? ':---'
                        : '---';
        })));
        rows.slice(1).forEach(function (row) {
            var cells = Array.prototype.slice.call(row.cells).map(cell);
            while (cells.length < head.length) {
                cells.push('');
            }
            lines.push(line(cells));
        });
        return lines.join('\n') + '\n';
    }-*/;

    // Copies the text, and shows on the element that it was.
    private void copy(final String text, final Element shown) {
        if (ClipboardUtil.copy(text)) {
            CopiedFlash.show(shown);
        }
    }

    private boolean onFrameLinkClick(final String href) {
        return getUiHandlers() != null && getUiHandlers().onFrameLinkClick(href);
    }

    /**
     * Offers the links clicked in the frame's document to {@link #onFrameLinkClick(String)} first, and stops the
     * frame following one that has been opened some other way: in a sandbox without scripts, a link to Stroom
     * would load Stroom in the frame, where it can't run. A link to a place in the page, and one clicked with a
     * modifier or another button (to open it in a new tab), are left to the browser. Does nothing for a document
     * this page can't see into.
     */
    private native void forwardLinkClicks(Element frameElement) /*-{
        var self = this;
        var doc = null;
        try {
            doc = frameElement.contentDocument;
        } catch (e) {
            // Another origin.
        }
        if (doc) {
            doc.addEventListener('click', function (event) {
                if (event.defaultPrevented || event.button !== 0
                    || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
                    return;
                }
                var target = event.target && event.target.closest ? event.target : null;
                // Rendered markdown's copy buttons and code spans (see MarkdownConverter): copied from here, as
                // the frame's document has no script of its own.
                var button = target ? target.closest('.markdown-copy') : null;
                if (button) {
                    var block = button.parentNode.querySelector('pre, table');
                    if (block) {
                        // A table as markdown; a code block's text as it is.
                        self.@stroom.iframe.client.view.IFrameViewImpl::copy(*)(block.tagName === 'TABLE'
                            ? @stroom.iframe.client.view.IFrameViewImpl::tableMarkdown(*)(block)
                            : block.textContent, button);
                    }
                    event.preventDefault();
                    return;
                }
                var code = target ? target.closest('code.markdown-copy-code') : null;
                var selected = doc.getSelection ? doc.getSelection().toString() : '';
                if (code && !code.closest('a') && !selected) {
                    self.@stroom.iframe.client.view.IFrameViewImpl::copy(*)(code.textContent, code);
                    return;
                }
                var link = target ? target.closest('a[href]') : null;
                if (!link || (link.getAttribute('href') || '').charAt(0) === '#') {
                    return;
                }
                if (self.@stroom.iframe.client.view.IFrameViewImpl::onFrameLinkClick(*)(link.href)) {
                    event.preventDefault();
                }
            });
        }
    }-*/;

    @Override
    public Widget asWidget() {
        return panel;
    }

    @Override
    public void setId(final String id) {
        frame.getElement().setId(id);
    }

    @Override
    public void setUrl(final String url) {
        lastTitle = url;
        frame.setUrl(url);
    }

    @Override
    public void setSrcDoc(final String html) {
        frame.getElement().setAttribute(SOURCE_DOC_ATTRIBUTE, html);
    }

    @Override
    public void setSandboxEnabled(final boolean isEnabled, final SandboxOption... sandboxOptions) {
        final Element element = frame.getElement();
        if (isEnabled) {
            // allow-popups so links work, e.g linking to external sites. Sandbox prevents any script execution.
            final String optionsStr = sandboxOptions == null
                    ? ""
                    : Arrays.stream(sandboxOptions)
                            .map(SandboxOption::getOption)
                            .collect(Collectors.joining(" "));

            element.setAttribute(SANDBOX_ATTRIBUTE, optionsStr);
        } else if (element.hasAttribute(SANDBOX_ATTRIBUTE)) {
            element.removeAttribute(SANDBOX_ATTRIBUTE);
        }
    }

    public void setCustomTitle(final String customTitle) {
        this.customTitle = customTitle;
    }

    @Override
    public String getTitle() {
        if (customTitle != null) {
            return customTitle;
        }
        return lastTitle;
    }

    @Override
    public void cleanup() {
        updateTitle = false;
    }
}
