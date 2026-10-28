/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.util.client;

import stroom.widget.text.client.Link;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.UriUtils;

/// Links that open in a new browser tab (`target="_blank"`), which tell screen readers so.
///
/// Each such link ends with the visually hidden text " (opens in new tab)" (in the `sr-only`
/// class), so its name says where it goes and that it leaves the page (WCAG 2.4.4, Link
/// Purpose), while sighted users see only the link's text. The hint is in the link's content,
/// not only in its title, which screen readers don't reliably announce. Each link also gets
/// `rel="noopener noreferrer"`, so the new tab can't reach back into Stroom.
///
/// Use [#link(String, String)] for a link built as HTML, [#setLink(Anchor, String, String)] for an
/// [Anchor] widget and [#addHintToNewTabLinks(String)] for HTML from elsewhere, e.g. rendered
/// Markdown.
public final class NewTabLink {

    /// The `target` of a link that opens in a new browser tab.
    public static final String TARGET = "_blank";

    /// The `rel` of a link that opens in a new browser tab.
    public static final String REL = "noopener noreferrer";

    /// The text that tells screen readers a link opens in a new browser tab.
    public static final String HINT = "(opens in new tab)";

    /// The class that hides text from sight but not from screen readers.
    public static final String HIDDEN_TEXT_CLASS = "sr-only";

    // The hint as it ends a link's content. The leading space keeps it apart from the link's text
    private static final String HIDDEN_HINT_HTML = "<span class=\"" + HIDDEN_TEXT_CLASS + "\"> "
                                                   + HINT + "</span>";
    private static final String ANCHOR_START = "<a";
    private static final String ANCHOR_END = "</a>";
    private static final String TARGET_ATTRIBUTE = "target";
    private static final String REL_ATTRIBUTE = "rel";

    private NewTabLink() {
        // Static utility
    }

    /// A link that opens in a new browser tab, e.g. `<a href="https://x" target="_blank"
    /// rel="noopener noreferrer">Quick filter documentation<span class="sr-only"> (opens in new
    /// tab)</span></a>`.
    ///
    /// @param url  The URL the link goes to. An unsafe URL (e.g. `javascript:`) is replaced with
    ///             `#`.
    /// @param text The link's text, escaped. If null or empty, the URL is the text, so the link
    ///             is never empty.
    /// @return The link's HTML.
    public static SafeHtml link(final String url, final String text) {
        final String linkText = text == null || text.isEmpty()
                ? url
                : text;
        // The start tag is split around the escaped URL, so its parts aren't complete HTML, which
        // appendHtmlConstant requires
        return new SafeHtmlBuilder()
                .append(SafeHtmlUtils.fromTrustedString("<a href=\""))
                .appendEscaped(UriUtils.sanitizeUri(url))
                .append(SafeHtmlUtils.fromTrustedString("\" target=\"" + TARGET + "\" rel=\"" + REL + "\">"))
                .append(content(linkText))
                .append(SafeHtmlUtils.fromTrustedString(ANCHOR_END))
                .toSafeHtml();
    }

    /// The content of a link that opens in a new browser tab: its text, escaped, followed by the
    /// visually hidden hint.
    ///
    /// @param text The link's text.
    /// @return The link's content as HTML.
    public static SafeHtml content(final String text) {
        return new SafeHtmlBuilder()
                .appendEscaped(text == null
                        ? ""
                        : text)
                .appendHtmlConstant(HIDDEN_HINT_HTML)
                .toSafeHtml();
    }

    /// Makes an [Anchor] widget a link that opens in a new browser tab, with the hint after its
    /// text. Use it in place of the anchor's `setText`, `setHref` and `setTarget`, as `setText`
    /// would remove the hint.
    ///
    /// @param anchor The anchor.
    /// @param url    The URL the link goes to.
    /// @param text   The link's text.
    public static void setLink(final Link anchor, final String url, final String text) {
        anchor.setHref(UriUtils.sanitizeUri(url));
        anchor.setTarget(TARGET);
        anchor.getElement().setAttribute(REL_ATTRIBUTE, REL);
        anchor.setHTML(content(text));
    }

    /// The visually hidden hint on its own, " (opens in new tab)" in the `sr-only` class, for
    /// content that opens a new browser tab without being a link, e.g. a menu item. Put it at the
    /// end of the content that names the control.
    ///
    /// @return The hint as HTML.
    public static SafeHtml hiddenHint() {
        return SafeHtmlUtils.fromSafeConstant(HIDDEN_HINT_HTML);
    }

    /// A name for a control that opens a page in a new browser tab, e.g. a button's title, as
    /// "Help on Data Types (opens in new tab)".
    ///
    /// @param name The control's name.
    /// @return The name followed by the hint, or the name unchanged if it already has it.
    public static String nameWithHint(final String name) {
        if (name == null || name.isEmpty()) {
            return HINT;
        } else if (name.endsWith(HINT)) {
            return name;
        } else {
            return name + " " + HINT;
        }
    }

    /// Adds the visually hidden hint to every link in some HTML that opens in a new browser tab
    /// (`target="_blank"`, in any case or quoting), e.g. Markdown that Showdown rendered with
    /// `openLinksInNewWindow`. A link that already has the hint is left alone, as are links that
    /// open in the same tab. A new tab link with no `rel` is given `rel="noopener noreferrer"`.
    ///
    /// @param html The HTML.
    /// @return The HTML with the hint added, or the HTML unchanged if it has no new tab links.
    public static String addHintToNewTabLinks(final String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        final String lowerCase = toLowerCase(html);
        final StringBuilder sb = new StringBuilder(html.length());
        int copiedTo = 0;
        int start = findAnchorStart(lowerCase, 0);
        while (start != -1) {
            final int tagEnd = findTagEnd(lowerCase, start);
            if (tagEnd == -1) {
                break;
            }
            final int end = lowerCase.indexOf(ANCHOR_END, tagEnd);
            if (end == -1) {
                break;
            }
            final String tag = lowerCase.substring(start, tagEnd);
            final String content = lowerCase.substring(tagEnd + 1, end);
            if (opensNewTab(tag) && !content.contains(HINT)) {
                if (findAttribute(tag, REL_ATTRIBUTE) == -1) {
                    // Copy the tag without its '>', then add the rel
                    sb.append(html, copiedTo, tagEnd);
                    sb.append(" " + REL_ATTRIBUTE + "=\"" + REL + "\"");
                    copiedTo = tagEnd;
                }
                sb.append(html, copiedTo, end);
                sb.append(HIDDEN_HINT_HTML);
                copiedTo = end;
            }
            start = findAnchorStart(lowerCase, end + ANCHOR_END.length());
        }
        if (copiedTo == 0) {
            return html;
        }
        sb.append(html, copiedTo, html.length());
        return sb.toString();
    }

    // Lower case a character at a time, so each index is the same as in the original (a whole
    // string's lower case can be longer, e.g. for 'İ')
    private static String toLowerCase(final String html) {
        final char[] chars = html.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    // The index of the next '<a' that starts an anchor's start tag (not e.g. '<abbr'), or -1
    private static int findAnchorStart(final String lowerCase, final int from) {
        int index = lowerCase.indexOf(ANCHOR_START, from);
        while (index != -1) {
            final int next = index + ANCHOR_START.length();
            if (next < lowerCase.length() && isTagNameEnd(lowerCase.charAt(next))) {
                return index;
            }
            index = lowerCase.indexOf(ANCHOR_START, next);
        }
        return -1;
    }

    // The index of the '>' that ends the start tag at `start`, skipping any in quoted values, or -1
    private static int findTagEnd(final String lowerCase, final int start) {
        char quote = 0;
        for (int i = start; i < lowerCase.length(); i++) {
            final char chr = lowerCase.charAt(i);
            if (quote != 0) {
                if (chr == quote) {
                    quote = 0;
                }
            } else if (chr == '"' || chr == '\'') {
                quote = chr;
            } else if (chr == '>') {
                return i;
            }
        }
        return -1;
    }

    private static boolean opensNewTab(final String tag) {
        final int index = findAttribute(tag, TARGET_ATTRIBUTE);
        if (index == -1) {
            return false;
        }
        int i = skipSpaces(tag, index + TARGET_ATTRIBUTE.length());
        if (i >= tag.length() || tag.charAt(i) != '=') {
            return false;
        }
        i = skipSpaces(tag, i + 1);
        if (i < tag.length() && (tag.charAt(i) == '"' || tag.charAt(i) == '\'')) {
            i++;
        }
        return tag.startsWith(TARGET, i);
    }

    // The index of an attribute's name in a start tag (as a whole name), or -1
    private static int findAttribute(final String tag, final String name) {
        int index = tag.indexOf(name);
        while (index != -1) {
            final int after = index + name.length();
            final boolean startsName = index > 0 && Character.isWhitespace(tag.charAt(index - 1));
            final boolean endsName = after >= tag.length()
                                     || tag.charAt(after) == '='
                                     || Character.isWhitespace(tag.charAt(after));
            if (startsName && endsName) {
                return index;
            }
            index = tag.indexOf(name, after);
        }
        return -1;
    }

    private static int skipSpaces(final String text, final int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isTagNameEnd(final char chr) {
        return chr == '>' || chr == '/' || Character.isWhitespace(chr);
    }
}
