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

package stroom.entity.client.presenter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/// The rules for sanitising HTML that has been rendered from untrusted markdown, e.g. the output of an AI model.
///
/// This class holds only the decisions (which elements, attributes and URLs are allowed) so that they can be
/// unit tested without a browser. The walk over the parsed DOM is done in [UntrustedHtmlSanitiser].
///
/// Remote images are never loaded, as loading one would send data to a server of the model's choosing
/// (e.g. `![](https://example.com/?data=...)`). They are replaced with a link that the user can choose to follow.
public final class HtmlSanitisePolicy {

    private static final Set<String> KEEP_ELEMENTS = new HashSet<>(Arrays.asList(
            "a", "b", "blockquote", "br", "code", "del", "details", "div", "em", "h1", "h2", "h3", "h4", "h5",
            "h6", "hr", "i", "input", "kbd", "li", "ol", "p", "pre", "s", "span", "strike", "strong", "sub",
            "summary", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "u", "ul"));

    private static final Set<String> DROP_ELEMENTS = new HashSet<>(Arrays.asList(
            "applet", "audio", "base", "button", "canvas", "embed", "form", "frame", "frameset", "head",
            "iframe", "link", "math", "meta", "noscript", "object", "option", "picture", "script", "select",
            "source", "style", "svg", "template", "textarea", "title", "track", "video"));

    private static final Set<String> CLASS_ELEMENTS = new HashSet<>(Arrays.asList("code", "pre", "span"));

    private static final Set<String> ALLOWED_IMAGE_DATA_PREFIXES = new HashSet<>(Arrays.asList(
            "data:image/png;", "data:image/jpeg;", "data:image/gif;", "data:image/webp;"));

    private HtmlSanitisePolicy() {
        // Static rules only.
    }

    /// What to do with an element when sanitising.
    public enum ElementAction {
        /// Keep the element, subject to its attributes being filtered.
        KEEP,
        /// Remove the element but keep its children.
        UNWRAP,
        /// Remove the element and everything inside it.
        DROP,
        /// The element is an image, which is replaced by [#imageReplacementUrl(String)].
        IMAGE
    }

    /// @param tagName The element's tag name, in any case.
    /// @return What to do with an element of this type.
    public static ElementAction getElementAction(final String tagName) {
        if (tagName == null) {
            return ElementAction.DROP;
        }
        final String tag = tagName.toLowerCase();
        if ("img".equals(tag)) {
            return ElementAction.IMAGE;
        } else if (KEEP_ELEMENTS.contains(tag)) {
            return ElementAction.KEEP;
        } else if (DROP_ELEMENTS.contains(tag)) {
            return ElementAction.DROP;
        } else {
            return ElementAction.UNWRAP;
        }
    }

    /// Decides whether an attribute may stay on an element that is being kept.
    ///
    /// @param tagName  The element's tag name, in any case.
    /// @param attrName The attribute name, in any case.
    /// @param value    The attribute value.
    /// @return The value to keep, or null if the attribute must be removed.
    public static String filterAttribute(final String tagName, final String attrName, final String value) {
        if (tagName == null || attrName == null) {
            return null;
        }
        final String tag = tagName.toLowerCase();
        final String attr = attrName.toLowerCase();
        final String val = value == null
                ? ""
                : value;

        switch (attr) {
            case "href":
                return "a".equals(tag) && isSafeLinkUrl(val)
                        ? val.trim()
                        : null;
            case "title":
                return "a".equals(tag)
                        ? val
                        : null;
            case "class":
                return CLASS_ELEMENTS.contains(tag) && isSafeClassList(val)
                        ? val
                        : null;
            case "align":
                return ("td".equals(tag) || "th".equals(tag)) && isAlignment(val)
                        ? val.toLowerCase()
                        : null;
            case "style":
                return ("td".equals(tag) || "th".equals(tag))
                        ? filterTextAlignStyle(val)
                        : null;
            case "type":
                return "input".equals(tag) && "checkbox".equalsIgnoreCase(val)
                        ? "checkbox"
                        : null;
            case "checked":
                return "input".equals(tag)
                        ? "checked"
                        : null;
            case "start":
                return "ol".equals(tag) && isDigits(val)
                        ? val
                        : null;
            case "open":
                return "details".equals(tag)
                        ? "open"
                        : null;
            default:
                return null;
        }
    }

    /// @param tagName The element's tag name, in any case.
    /// @param type    The value of the element's `type` attribute, or null.
    /// @return True if an `input` element of this type may be kept. Only checkboxes (from markdown task lists)
    /// are allowed, and they are always made read only.
    public static boolean isAllowedInput(final String tagName, final String type) {
        return "input".equalsIgnoreCase(tagName) && "checkbox".equalsIgnoreCase(type);
    }

    /// @param url A URL from an `href` attribute.
    /// @return True if a link to this URL is safe to show. Only absolute http, https and mailto URLs, links to
    /// anchors within the page, and links to the page itself with a query (such as Stroom's own
    /// `?action=open-doc&...`, which the chat opens in the current session) are allowed.
    public static boolean isSafeLinkUrl(final String url) {
        if (url == null) {
            return false;
        }
        final String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("#") || trimmed.startsWith("?")) {
            // The page itself, so no scheme that could run anything.
            return true;
        }
        // Browsers ignore tabs, new lines and control characters within a scheme, e.g. "java\tscript:".
        final String compact = removeWhitespaceAndControlChars(trimmed).toLowerCase();
        return compact.startsWith("https://")
               || compact.startsWith("http://")
               || compact.startsWith("mailto:");
    }

    /// @param src The `src` attribute of an image.
    /// @return True if the image can be shown without loading anything from a server, i.e. it is an inline
    /// raster image.
    public static boolean isInlineImage(final String src) {
        if (src == null) {
            return false;
        }
        final String compact = removeWhitespaceAndControlChars(src).toLowerCase();
        for (final String prefix : ALLOWED_IMAGE_DATA_PREFIXES) {
            if (compact.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /// @param src The `src` attribute of a remote image.
    /// @return The URL to link to in place of the image, or null if it is not safe to link to it at all.
    public static String imageReplacementUrl(final String src) {
        return isSafeLinkUrl(src)
                ? src.trim()
                : null;
    }

    /// @param alt The `alt` text of an image that is not being loaded.
    /// @return The text to show in place of the image.
    public static String imageReplacementText(final String alt) {
        final String trimmed = alt == null
                ? ""
                : alt.trim();
        return trimmed.isEmpty()
                ? "[Image not loaded]"
                : "[Image not loaded: " + trimmed + "]";
    }

    private static boolean isSafeClassList(final String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            final boolean ok = (chr >= 'a' && chr <= 'z')
                               || (chr >= 'A' && chr <= 'Z')
                               || (chr >= '0' && chr <= '9')
                               || chr == '-'
                               || chr == '_'
                               || chr == ' ';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlignment(final String value) {
        final String lower = value.trim().toLowerCase();
        return "left".equals(lower) || "right".equals(lower) || "center".equals(lower);
    }

    private static String filterTextAlignStyle(final String value) {
        final String compact = removeWhitespaceAndControlChars(value).toLowerCase();
        final String withoutSemi = compact.endsWith(";")
                ? compact.substring(0, compact.length() - 1)
                : compact;
        if (withoutSemi.startsWith("text-align:")) {
            final String alignment = withoutSemi.substring("text-align:".length());
            if (isAlignment(alignment)) {
                return "text-align:" + alignment + ";";
            }
        }
        return null;
    }

    private static boolean isDigits(final String value) {
        if (value.isEmpty() || value.length() > 9) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static String removeWhitespaceAndControlChars(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (chr > ' ' && chr != '\u007f') {
                sb.append(chr);
            }
        }
        return sb.toString();
    }
}
