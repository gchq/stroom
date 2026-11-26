/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.client;

import java.util.function.Function;

public final class RedirectUrlUtil {

    private RedirectUrlUtil() {
        // Utility class.
    }

    /// Checks whether a redirect target is a safe root-relative path or an absolute HTTP(S) URL
    /// on the current page's origin. The browser normalises default ports when comparing origins.
    ///
    /// @param uri the redirect target, which may be null
    /// @return whether the target can be used for a same-origin redirect
    public static boolean isSameOrigin(final String uri) {
        return isSameOrigin(uri, getPageOrigin(), RedirectUrlUtil::getAbsoluteHttpOrigin);
    }

    /// Checks a redirect target using the supplied origin parser for absolute URLs.
    ///
    /// @param uri the redirect target, which may be null
    /// @param pageOrigin the current page's origin
    /// @param absoluteHttpOrigin a parser returning the origin of an absolute HTTP(S) URL, or null
    /// @return whether the target can be used for a same-origin redirect
    static boolean isSameOrigin(final String uri,
                                final String pageOrigin,
                                final Function<String, String> absoluteHttpOrigin) {
        return isSafeRootRelativePath(uri) || (uri != null && pageOrigin.equals(absoluteHttpOrigin.apply(uri)));
    }

    private static native String getPageOrigin() /*-{
        return $wnd.location.origin;
    }-*/;

    private static native String getAbsoluteHttpOrigin(final String uri) /*-{
        try {
            var url = new $wnd.URL(uri);
            if (url.protocol === "https:" || url.protocol === "http:") {
                return url.origin;
            }
        } catch (e) {
            // The URL constructor rejects relative and malformed targets.
        }
        return null;
    }-*/;

    /**
     * Whether a redirect target is a root-relative path that cannot escape the current origin, and so is safe
     * to navigate to after login without an origin comparison.
     * <p>
     * The value must start with a single {@code '/'} and contain no backslash or control characters. A
     * browser treats a backslash as a {@code '/'} and strips control characters (tab, newline, ...) before
     * parsing, so a value such as {@code "/\evil.com"} resolves to {@code "//evil.com"} - a protocol-relative
     * URL to a different origin - even though it starts with a single {@code '/'}. Rejecting those characters
     * keeps the value a genuine same-origin path.
     */
    public static boolean isSafeRootRelativePath(final String uri) {
        if (uri == null || !uri.startsWith("/") || uri.startsWith("//")) {
            return false;
        }
        for (int i = 0; i < uri.length(); i++) {
            final char c = uri.charAt(i);
            if (c == '\\' || c < 0x20 || c == 0x7f) {
                return false;
            }
        }
        return true;
    }
}
