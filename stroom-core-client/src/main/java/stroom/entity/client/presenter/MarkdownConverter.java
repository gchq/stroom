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

import stroom.preferences.client.UserPreferencesManager;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.shared.ThemeType;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class MarkdownConverter {

    // The level untrusted markdown's top headings (an AI answer's) are shown at, the rest below them.
    private static final int UNTRUSTED_TOP_HEADING = 3;

    private final UserPreferencesManager userPreferencesManager;
    private final JavaScriptObject showdownConverter;

    @Inject
    public MarkdownConverter(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
        this.showdownConverter = initConverter();
    }

    /**
     * Converts the supplied markdown into html with appropriate syntax highlighting.
     * The markdown is surrounded by a container div.
     * <p><b>NOTE:</b> if the markdown has come from user content then it cannot be trusted, so
     * you should contain the resulting html in a sandboxed iframe or add other layers of protection
     * from scripting in the markdown.</p>
     */
    public SafeHtml convertMarkdownToHtml(final String rawMarkdown) {
        return convertMarkdownToHtml(rawMarkdown, false);
    }

    private SafeHtml convertMarkdownToHtml(final String rawMarkdown, final boolean copyButtons) {
        if (NullSafe.isBlankString(rawMarkdown)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
            appendMarkdownInDiv(htmlBuilder, rawMarkdown, copyButtons);
            return htmlBuilder.toSafeHtml();
        }
    }

    /**
     * Converts the supplied markdown into html with appropriate syntax highlighting
     * The markdown is surrounded by a container div and placed in a sandboxed iFrame for
     * safety from scripts.
     */
    public SafeHtml convertMarkdownToHtmlInFrame(final String rawMarkdown) {
        if (NullSafe.isBlankString(rawMarkdown)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {

            // A copy button on each code block and table: the frame's document has no script, so the clicks are
            // handled from outside it (IFrameViewImpl).
            final SafeHtml markdownSafeHtml = convertMarkdownToHtml(rawMarkdown, true);
            final String currentPreferenceClasses = userPreferencesManager.getCurrentPreferenceClasses();
            final SafeHtml iFrameHtmlContent = new SafeHtmlBuilder()
                    .appendHtmlConstant("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .appendHtmlConstant("<!DOCTYPE html>")
                    .appendHtmlConstant("<html class=\"")
                    .append(SafeHtmlUtil.from(currentPreferenceClasses))
                    .appendHtmlConstant("\">")
                    .appendHtmlConstant("<head>")
                    .appendHtmlConstant("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">")
                    .appendHtmlConstant("<link rel=\"stylesheet\" href=\"ui/css/app.css\" type=\"text/css\" />")
                    .appendHtmlConstant("</head>")
                    .appendHtmlConstant("<body>")
                    .appendHtmlConstant("<div class=\"max info-page markdown-container\">")
                    .append(markdownSafeHtml)
                    .appendHtmlConstant("</div>")
                    .appendHtmlConstant("</body>")
                    .appendHtmlConstant("</html>")
                    .toSafeHtml();

            return iFrameHtmlContent;
        }
    }

    /**
     * Converts the supplied markdown into html with appropriate syntax highlighting and
     * appends it to the htmlBuilder as a div with the appropriate classes for markdown rendering.
     * <p><b>NOTE:</b> if the markdown has come from user content then it cannot be trusted, so
     * you should contain the resulting html in a sandboxed iframe or add other layers of protection
     * from scripting in the markdown.</p>
     */
    public void appendMarkdownInDiv(final HtmlBuilder htmlBuilder, final String rawMarkdown) {
        appendMarkdownInDiv(htmlBuilder, rawMarkdown, false);
    }

    private void appendMarkdownInDiv(final HtmlBuilder htmlBuilder,
                                     final String rawMarkdown,
                                     final boolean copyButtons) {
        if (htmlBuilder != null && !NullSafe.isBlankString(rawMarkdown)) {
            final String markdownHtml = nativeConvert(rawMarkdown, copyButtons
                    ? SvgImage.COPY.getSvg()
                    : null);
            final SafeHtml markdownSafeHtml = SafeHtmlUtils.fromTrustedString(markdownHtml);
            final String cssClasses = getMarkdownContainerClasses();

            htmlBuilder.div(builder -> builder.append(markdownSafeHtml),
                    Attribute.className(cssClasses));
        }
    }

    /// Converts markdown from an untrusted source (e.g. an AI model) into sanitised html, surrounded by a
    /// container div.
    ///
    /// The html is built in an inert document, so nothing in it can run or load while it is being processed. It is
    /// then sanitised with [HtmlSanitisePolicy]: scripts, event handlers, unsafe links and styling are removed, and
    /// remote images are replaced with links so that nothing is fetched from a server without the user choosing to.
    ///
    /// @param rawMarkdown The untrusted markdown.
    /// @return The sanitised html, or empty html if the markdown is blank.
    public SafeHtml convertUntrustedMarkdownToHtml(final String rawMarkdown) {
        if (NullSafe.isBlankString(rawMarkdown)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
            final Element container = nativeParseInert(nativeMakeHtml(rawMarkdown));
            UntrustedHtmlSanitiser.sanitise(container);
            // After sanitising: the sections and copy hints are the converter's own, not the markdown's.
            nativeCopyHints(container);
            // An answer's headings below the chat's own (asked for by the user: an answer's "##" as h2 looked too
            // large in the chat).
            nativeDemoteHeadings(container, UNTRUSTED_TOP_HEADING);
            nativeSections(container);
            final String sanitisedHtml = container.getInnerHTML();
            final SafeHtml markdownSafeHtml = SafeHtmlUtils.fromTrustedString(sanitisedHtml);
            htmlBuilder.div(builder -> builder.append(markdownSafeHtml),
                    Attribute.className(getMarkdownContainerClasses()));
            return htmlBuilder.toSafeHtml();
        }
    }

    public ThemeType geCurrentThemeType() {
        return userPreferencesManager.geCurrentThemeType();
    }

    private native JavaScriptObject initConverter() /*-{
        // See https://showdownjs.com/docs/available-options/
        var converter = new $wnd.showdown.Converter({
            openLinksInNewWindow: true,
            strikethrough: true,
            tables: true,
            tasklists: true,
        });

        return converter;
    }-*/;

    private native String nativeMakeHtml(final String rawMarkdown) /*-{
        var converter = this.@stroom.entity.client.presenter.MarkdownConverter::showdownConverter;
        return converter.makeHtml(rawMarkdown);
    }-*/;

    public String nativeConvertMarkdownToHtml(final String rawMarkdown) {
        return nativeConvert(rawMarkdown, null);
    }

    // The markdown as html, with a copy button (the given icon) on each code block and table, or none for null.
    private native String nativeConvert(final String rawMarkdown, final String copyIcon) /*-{
        var converter = this.@stroom.entity.client.presenter.MarkdownConverter::showdownConverter;
        var markdownAsHtml = converter.makeHtml(rawMarkdown);

        // Transient div just so we can tokenise and section the existing html
        var markdownDiv = $doc.createElement("div");
        markdownDiv.innerHTML = markdownAsHtml;
        // Only need to involve prism if showdown has set a language-XXX class
        if (/language-/.test(markdownAsHtml)) {
            // This will run the prismjs code to tokenise the content of fenced blocks which
            // are within pre/code. It will surround the different bits of syntax with appropriately classed
            // span tags. ShowdownJS will have set the appropriate language class on the
            // code tag so then the prism css can style all the created spans.
            $wnd.Prism.highlightAllUnder(markdownDiv);
        }
        this.@stroom.entity.client.presenter.MarkdownConverter::nativeCopyHints(*)(markdownDiv);
        this.@stroom.entity.client.presenter.MarkdownConverter::nativeKeepCellWordsWhole(*)(markdownDiv);
        if (copyIcon) {
            this.@stroom.entity.client.presenter.MarkdownConverter::nativeCopyButtons(*)(markdownDiv, copyIcon);
        }
        this.@stroom.entity.client.presenter.MarkdownConverter::nativeSections(*)(markdownDiv);
        return markdownDiv.innerHTML;
    }-*/;

    /// Keeps each run of text without a space in a table cell on one line, when it holds a character a browser may
    /// break a line after (a hyphen, a slash, ...), e.g. `2026-10-25` (asked for by the user); a cell still wraps at
    /// its spaces. CSS can't stop a break after a hyphen alone, so each such run is put in a span that doesn't wrap.
    /// Its text is as it was, so what is copied is too.
    ///
    /// @param container The element holding the converted html.
    private native void nativeKeepCellWordsWhole(final Element container) /*-{
        var doc = container.ownerDocument;
        var breakable = /[^\s\w]/;
        Array.prototype.forEach.call(container.querySelectorAll('td, th'), function (cell) {
            var walker = doc.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
            var texts = [];
            while (walker.nextNode()) {
                texts.push(walker.currentNode);
            }
            texts.forEach(function (text) {
                var parts = text.nodeValue.split(/(\s+)/);
                if (!parts.some(function (part) {
                    return /\S/.test(part) && breakable.test(part);
                })) {
                    return;
                }
                var fragment = doc.createDocumentFragment();
                parts.forEach(function (part) {
                    if (/\S/.test(part) && breakable.test(part)) {
                        var whole = doc.createElement('span');
                        whole.className = 'markdown-nowrap';
                        whole.textContent = part;
                        fragment.appendChild(whole);
                    } else if (part) {
                        fragment.appendChild(doc.createTextNode(part));
                    }
                });
                text.parentNode.replaceChild(fragment, text);
            });
        });
    }-*/;

    /// Marks each code span (code not in a block) as copied by a click, with a hint saying so (asked for by the
    /// user: a TypeId such as `Secret-Checkin` to copy). The click is handled where the html is shown: the chat,
    /// or, for a frame, [stroom.iframe.client.view.IFrameViewImpl].
    ///
    /// @param container The element holding the converted html.
    private native void nativeCopyHints(final Element container) /*-{
        var codes = container.querySelectorAll('code');
        Array.prototype.forEach.call(codes, function (code) {
            if (!code.closest('pre')) {
                code.classList.add('markdown-copy-code');
                code.setAttribute('title', 'Click to copy');
            }
        });
    }-*/;

    /// Puts a copy button at the top right of each code block and table (asked for by the user), each in a
    /// wrapper the button sits in. A table is copied as markdown, a code block as its text.
    ///
    /// @param container The element holding the converted html.
    /// @param icon      The button's icon, as svg.
    private native void nativeCopyButtons(final Element container, final String icon) /*-{
        var doc = container.ownerDocument;
        var blocks = container.querySelectorAll('pre, table');
        Array.prototype.forEach.call(blocks, function (block) {
            if (block.parentNode.closest('.markdown-copyable')) {
                return;
            }
            var wrapper = doc.createElement('div');
            wrapper.className = 'markdown-copyable';
            block.parentNode.insertBefore(wrapper, block);
            if (block.tagName === 'TABLE') {
                // A table too wide for the page scrolls sideways in a box of its own, its button staying put.
                var scroll = doc.createElement('div');
                scroll.className = 'markdown-table-scroll';
                scroll.appendChild(block);
                wrapper.appendChild(scroll);
            } else {
                wrapper.appendChild(block);
            }
            var button = doc.createElement('button');
            button.type = 'button';
            button.className = 'markdown-copy';
            button.title = block.tagName === 'TABLE'
                ? 'Copy the table as markdown'
                : 'Copy';
            button.innerHTML = icon;
            wrapper.appendChild(button);
        });
    }-*/;

    /// Moves the headings down so the highest is at the given level, the rest keeping their places below it (at
    /// most h6); headings already at or below it are left as they are.
    ///
    /// @param container The element holding the converted html.
    /// @param top       The level the highest heading is shown at, e.g. 3.
    private native void nativeDemoteHeadings(final Element container, final int top) /*-{
        var headings = container.querySelectorAll('h1, h2, h3, h4, h5, h6');
        var highest = 7;
        Array.prototype.forEach.call(headings, function (heading) {
            highest = Math.min(highest, parseInt(heading.tagName.substring(1), 10));
        });
        var shift = top - highest;
        if (headings.length === 0 || shift <= 0) {
            return;
        }
        var doc = container.ownerDocument;
        Array.prototype.forEach.call(headings, function (heading) {
            var level = Math.min(6, parseInt(heading.tagName.substring(1), 10) + shift);
            var moved = doc.createElement('h' + level);
            Array.prototype.forEach.call(heading.attributes, function (attribute) {
                moved.setAttribute(attribute.name, attribute.value);
            });
            while (heading.firstChild) {
                moved.appendChild(heading.firstChild);
            }
            heading.parentNode.replaceChild(moved, heading);
        });
    }-*/;

    /// Makes each heading collapsible, expanded to start with: the heading, and what follows it up to the next
    /// heading of its level or above, as a `<details open>` whose `<summary>` is the heading, nested as the
    /// headings are (asked for by the user). Native, so it opens and closes without script, in a sandboxed frame
    /// too. Only headings at the top level are sectioned, not those in a list or a quote.
    ///
    /// @param container The element holding the converted html.
    private native void nativeSections(final Element container) /*-{
        var doc = container.ownerDocument;
        var nodes = Array.prototype.slice.call(container.childNodes);
        if (!nodes.some(function (node) {
            return node.nodeType === 1 && /^H[1-6]$/.test(node.tagName);
        })) {
            return;
        }
        while (container.firstChild) {
            container.removeChild(container.firstChild);
        }
        var open = [{level: 0, element: container}];
        nodes.forEach(function (node) {
            var heading = node.nodeType === 1 && /^H([1-6])$/.exec(node.tagName);
            if (!heading) {
                open[open.length - 1].element.appendChild(node);
                return;
            }
            var level = parseInt(heading[1], 10);
            while (open.length > 1 && open[open.length - 1].level >= level) {
                open.pop();
            }
            var section = doc.createElement("details");
            section.className = "markdown-section";
            section.setAttribute("open", "");
            var summary = doc.createElement("summary");
            summary.className = "markdown-section__heading";
            summary.appendChild(node);
            section.appendChild(summary);
            open[open.length - 1].element.appendChild(section);
            open.push({level: level, element: section});
        });
    }-*/;

    private native Element nativeParseInert(final String html) /*-{
        // A document made by DOMParser has no browsing context, so nothing in it runs or loads.
        var doc = new $wnd.DOMParser().parseFromString("<!DOCTYPE html><html><body></body></html>", "text/html");
        var container = doc.body;
        container.innerHTML = html;
        if (/language-/.test(html)) {
            $wnd.Prism.highlightAllUnder(container);
        }
        return container;
    }-*/;

    /**
     * @return The space separated list of css classes to set on the container div that holds
     * the markdown html. Its code's colours follow the page's theme (see prismjs-light.css, its rules under
     * .stroom-theme-light), so a theme changed while it is shown is followed.
     */
    private String getMarkdownContainerClasses() {
        return "markdown";
    }
}
