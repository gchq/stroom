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

    // The width the user gave rendered markdown's page by dragging its edge, in pixels, kept for each document
    // opened after; 0 for the stylesheet's.
    private static double markdownWidth;

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
            followTheme(frame.getElement());
            resizableWidth(frame.getElement());
            pinHeadings(frame.getElement());
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

    /// Pins rendered markdown's section headings (see MarkdownConverter) at the top of the frame as it scrolls,
    /// each below those of the sections it is in, so the headings of every section the view is in show, and any
    /// of them can be collapsed from there (asked for by the user). The frame's document has no script of its
    /// own, so each heading's place is measured from here: again when the frame is resized, its fonts load, or a
    /// section opens (a closed one's headings have no size). Does nothing for a document this page can't see
    /// into, or one with no sections.
    private native void pinHeadings(Element frameElement) /*-{
        var doc = null;
        try {
            doc = frameElement.contentDocument;
        } catch (e) {
            // Another origin.
        }
        if (!doc || !doc.querySelector('details.markdown-section')) {
            return;
        }
        var scroller = function () {
            var container = doc.querySelector('.markdown-container');
            return container && container.scrollHeight > container.clientHeight
                ? container
                : doc.scrollingElement || doc.documentElement;
        };
        var lowest = null;
        // The lowest heading pinned has a shadow beneath it, for a sense of depth (asked for by the user).
        var markLowest = function () {
            var found = null;
            Array.prototype.forEach.call(doc.querySelectorAll('.markdown-section__heading'), function (summary) {
                var top = summary.getBoundingClientRect().top;
                if (summary.parentElement.getBoundingClientRect().top < top - 0.5) {
                    found = summary;
                }
            });
            if (found !== lowest) {
                if (lowest) {
                    lowest.classList.remove('markdown-section__heading--lowest-pinned');
                }
                if (found) {
                    found.classList.add('markdown-section__heading--lowest-pinned');
                }
                lowest = found;
            }
        };
        var marking = false;
        var markSoon = function () {
            if (!marking) {
                marking = true;
                doc.defaultView.requestAnimationFrame(function () {
                    marking = false;
                    markLowest();
                });
            }
        };
        var place = function () {
            // Pinned at the very top, over the page's own padding, rather than leaving a gap above the headings.
            var padding = parseFloat(doc.defaultView.getComputedStyle(scroller()).paddingTop) || 0;
            var sections = doc.querySelectorAll('details.markdown-section');
            // Every height first, then every place, so the layout is worked out once.
            var heights = Array.prototype.map.call(sections, function (section) {
                return section.firstElementChild.getBoundingClientRect().height;
            });
            var placed = new Map();
            Array.prototype.forEach.call(sections, function (section, i) {
                var outer = section.parentElement.closest('details.markdown-section');
                var above = outer ? placed.get(outer) : {bottom: 0, depth: -1};
                var top = above.bottom;
                var depth = above.depth + 1;
                placed.set(section, {bottom: top + heights[i], depth: depth});
                var summary = section.firstElementChild;
                summary.style.top = (top - padding) + 'px';
                // An outer heading over an inner one that is scrolling away beneath it.
                summary.style.zIndex = String(Math.max(2, 20 - depth));
                if (summary.firstElementChild) {
                    // A link to a heading puts it below the headings pinned above it.
                    summary.firstElementChild.style.scrollMarginTop = (top - padding) + 'px';
                }
            });
            doc.documentElement.classList.add('markdown-pinned-headings');
            markSoon();
        };
        place();
        doc.addEventListener('toggle', place, true);
        doc.addEventListener('scroll', markSoon, {capture: true, passive: true});
        // As the page's width changes (the frame resized, or its edge dragged), its headings wrap differently.
        new doc.defaultView.ResizeObserver(place).observe(doc.querySelector('.markdown') || doc.body);
        if (doc.fonts && doc.fonts.ready) {
            doc.fonts.ready.then(place);
        }
        // A section collapsed from its pinned heading: scrolled to where it starts, its heading staying where it
        // was, rather than leaving the view further down the page, past it.
        doc.addEventListener('click', function (event) {
            var summary = event.target && event.target.closest
                ? event.target.closest('.markdown-section__heading')
                : null;
            if (!summary || event.defaultPrevented) {
                return;
            }
            var section = summary.parentElement;
            var pinned = summary.getBoundingClientRect().top - section.getBoundingClientRect().top > 1;
            if (!pinned || !section.open) {
                return;
            }
            var shown = summary.getBoundingClientRect().top;
            setTimeout(function () {
                scroller().scrollBy({top: section.getBoundingClientRect().top - shown, behavior: 'instant'});
            }, 0);
        });
    }-*/;

    /// Keeps a rendered markdown page (see MarkdownConverter) in this page's theme, density and font: its `<html>`
    /// is given this page's classes as it loads, and again each time they change (asked for by the user: a theme
    /// changed while a document was open left it in the old one). The frame's own classes (e.g. its pinned
    /// headings') are kept. Does nothing for a document this page can't see into, or one that isn't rendered
    /// markdown.
    private native void followTheme(Element frameElement) /*-{
        var doc = null;
        try {
            doc = frameElement.contentDocument;
        } catch (e) {
            // Another origin.
        }
        if (!doc || !doc.querySelector('.markdown-container')) {
            return;
        }
        var root = $doc.documentElement;
        var own = ['markdown-pinned-headings', 'markdown-resizing'];
        var follow = function () {
            var kept = own.filter(function (name) {
                return doc.documentElement.classList.contains(name);
            });
            doc.documentElement.className = (root.className + ' ' + kept.join(' ')).trim();
        };
        follow();
        var observer = new MutationObserver(function () {
            // A frame since loaded with another document has its own follower.
            if (frameElement.contentDocument !== doc) {
                observer.disconnect();
                return;
            }
            follow();
        });
        observer.observe(root, {attributes: true, attributeFilter: ['class']});
    }-*/;

    /// Lets the user change the width of a rendered markdown page (see MarkdownConverter) by dragging its right
    /// edge, a faint line (asked for by the user); a double-click on it puts back the stylesheet's width. The width
    /// given is kept for each document opened after. The frame's document has no script of its own, so the
    /// dragging is handled from here. Does nothing for a document this page can't see into, or one that isn't
    /// rendered markdown.
    private native void resizableWidth(Element frameElement) /*-{
        var doc = null;
        try {
            doc = frameElement.contentDocument;
        } catch (e) {
            // Another origin.
        }
        var page = doc ? doc.querySelector('.info-page.markdown-container > .markdown') : null;
        if (!page) {
            return;
        }
        // Narrower than this, a page is too cramped to read.
        var narrowest = 320;
        var kept = @stroom.iframe.client.view.IFrameViewImpl::markdownWidth;
        if (kept > 0) {
            page.style.maxWidth = kept + 'px';
        }
        var handle = doc.createElement('div');
        handle.className = 'markdown-width-handle';
        handle.title = 'Drag to change the width, double-click for the default';
        page.appendChild(handle);
        handle.addEventListener('pointerdown', function (event) {
            if (event.button !== 0) {
                return;
            }
            event.preventDefault();
            handle.setPointerCapture(event.pointerId);
            doc.documentElement.classList.add('markdown-resizing');
            var left = page.getBoundingClientRect().left;
            var move = function (moved) {
                var width = Math.max(narrowest, Math.round(moved.clientX - left));
                page.style.maxWidth = width + 'px';
                @stroom.iframe.client.view.IFrameViewImpl::markdownWidth = width;
            };
            var end = function () {
                handle.removeEventListener('pointermove', move);
                handle.removeEventListener('pointerup', end);
                handle.removeEventListener('pointercancel', end);
                doc.documentElement.classList.remove('markdown-resizing');
            };
            handle.addEventListener('pointermove', move);
            handle.addEventListener('pointerup', end);
            handle.addEventListener('pointercancel', end);
        });
        handle.addEventListener('dblclick', function () {
            page.style.maxWidth = '';
            @stroom.iframe.client.view.IFrameViewImpl::markdownWidth = 0;
        });
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
