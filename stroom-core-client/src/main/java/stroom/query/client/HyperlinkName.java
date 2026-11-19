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

package stroom.query.client;

import stroom.hyperlink.client.HyperlinkType;
import stroom.widget.hyperlink.client.LinkValue;

/// What a [LinkValue] in a table cell shows, and what screen readers call it.
///
/// A link is shown with its text and named by it. A link with no text is still shown, with a name
/// made from the rest of the link, so that it can be seen and followed. A link whose text is just
/// its URL is shown as it is but named more briefly (a screen reader would otherwise spell out the
/// whole URL). The name made from the rest of the link is, in order of preference:
///
/// * the title given with its type (`{tab|Title}` or `{dialog|Title}`, the title of the tab or
///   dialog it opens);
/// * for a URL (`scheme://...`), its host and path, without the scheme, query or fragment, e.g.
///   `example.com/docs/page` for `https://user@example.com/docs/page/?q=1#top`;
/// * for a link that opens part of Stroom from its parameters (a dashboard, data, stepping or an
///   annotation), what it opens, e.g. "Dashboard";
/// * its address as it is;
/// * "Link".
public final class HyperlinkName {

    static final String DEFAULT_NAME = "Link";

    private static final String SCHEME_SEPARATOR = "://";
    private static final char TITLE_SEPARATOR = '|';

    private HyperlinkName() {
        // Static utility
    }

    /// @param hyperlink A link.
    /// @return The text to show for the link: its text, or, when it has none, a name made from
    /// the rest of the link. Never null or blank.
    public static String getDisplayText(final LinkValue hyperlink) {
        final String text = trimToNull(hyperlink.getText());
        return text != null
                ? text
                : describe(hyperlink);
    }

    /// @param hyperlink A link.
    /// @return The link's accessible name when it should differ from the text shown (when the
    /// text is just its URL), or null when the text shown names it.
    public static String getAccessibleName(final LinkValue hyperlink) {
        final String text = trimToNull(hyperlink.getText());
        if (text != null && isUrlText(text, hyperlink.getHref())) {
            final String name = describe(hyperlink);
            return name.equals(text)
                    ? null
                    : name;
        }
        return null;
    }

    /// @param hyperlink A link.
    /// @return A name for the link made from all but its text. Never null or blank.
    static String describe(final LinkValue hyperlink) {
        final String title = getTitle(hyperlink.getType());
        if (title != null) {
            return title;
        }

        final String href = trimToNull(hyperlink.getHref());
        if (href != null && href.contains(SCHEME_SEPARATOR)) {
            final String hostAndPath = getHostAndPath(href);
            return hostAndPath != null
                    ? hostAndPath
                    : href;
        }

        final String opens = describeType(getTypeName(hyperlink.getType()));
        if (opens != null) {
            return opens;
        }
        return href != null
                ? href
                : DEFAULT_NAME;
    }

    /// @param url A URL with a scheme, e.g. `https://example.com/a?b=c`.
    /// @return Its host and path, without the scheme, any user, query, fragment or trailing
    /// slashes, e.g. `example.com/a`, or null if it has none.
    static String getHostAndPath(final String url) {
        final int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
        String rest = schemeEnd >= 0
                ? url.substring(schemeEnd + SCHEME_SEPARATOR.length())
                : url;

        rest = cutAt(rest, '#');
        rest = cutAt(rest, '?');

        // A user (and password) before the host
        final int pathStart = rest.indexOf('/');
        final int at = rest.indexOf('@');
        if (at >= 0 && (pathStart < 0 || at < pathStart)) {
            rest = rest.substring(at + 1);
        }

        return trimToNull(stripTrailingSlashes(rest));
    }

    // The title given after the type, e.g. 'My Tab' for 'tab|My Tab', as HyperlinkOpener
    // reads it
    private static String getTitle(final String type) {
        if (type == null) {
            return null;
        }
        final int index = type.indexOf(TITLE_SEPARATOR);
        return index >= 0
                ? trimToNull(type.substring(index + 1))
                : null;
    }

    private static String getTypeName(final String type) {
        if (type == null) {
            return null;
        }
        final int index = type.indexOf(TITLE_SEPARATOR);
        return trimToNull(index >= 0
                ? type.substring(0, index)
                : type);
    }

    // What a link that opens part of Stroom from its parameters opens, or null for a link to an
    // address (a URL in a tab, dialog or the browser) or an unknown type
    private static String describeType(final String typeName) {
        if (typeName == null) {
            return null;
        }
        final HyperlinkType hyperlinkType;
        try {
            hyperlinkType = HyperlinkType.valueOf(typeName.toUpperCase());
        } catch (final RuntimeException e) {
            return null;
        }
        switch (hyperlinkType) {
            case DASHBOARD:
                return "Dashboard";
            case DATA:
                return "Data";
            case STEPPING:
                return "Stepping";
            case ANNOTATION:
                return "Annotation";
            default:
                return null;
        }
    }

    // Whether the text is just a URL: the link's own address, or any address with a scheme and no
    // spaces
    private static boolean isUrlText(final String text, final String href) {
        final String trimmedHref = trimToNull(href);
        if (trimmedHref != null
            && stripTrailingSlashes(text).equalsIgnoreCase(stripTrailingSlashes(trimmedHref))) {
            return true;
        }
        final int schemeEnd = text.indexOf(SCHEME_SEPARATOR);
        return schemeEnd > 0 && text.indexOf(' ') < 0;
    }

    private static String stripTrailingSlashes(final String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String cutAt(final String value, final char c) {
        final int index = value.indexOf(c);
        return index >= 0
                ? value.substring(0, index)
                : value;
    }

    private static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}
