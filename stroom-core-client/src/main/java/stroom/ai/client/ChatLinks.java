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

package stroom.ai.client;

import stroom.core.client.UrlParameters;
import stroom.docref.DocRef;

import java.util.HashMap;
import java.util.Map;

/// Works out what a link in the chat points at.
///
/// A link to Stroom's own `?action=open-doc&docType=…&docUuid=…` (as the FastMCP server returns, and as the
/// model may write) is opened in the current Stroom session rather than followed, which would reload Stroom. It
/// is matched on its query alone, as the server may know Stroom by an internal address. No other `action` is
/// acted on.
public final class ChatLinks {

    // A doc type is a short name, e.g. 'Pipeline' or 'XMLSchema'.
    private static final String DOC_TYPE_PATTERN = "[A-Za-z][A-Za-z0-9]{0,63}";
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private ChatLinks() {
        // Static methods only.
    }

    /// @param href A link's `href`.
    /// @return The document the link opens, if it is a Stroom open-doc link with a well-formed doc type and UUID,
    /// or null otherwise. Whether the type is one Stroom knows is for the caller to check.
    public static DocRef openDocTarget(final String href) {
        if (href == null) {
            return null;
        }
        final Map<String, String> query = query(href.trim());
        if (query == null || !UrlParameters.OPEN_DOC_ACTION.equals(query.get(UrlParameters.ACTION))) {
            return null;
        }
        final String type = query.get(UrlParameters.DOC_TYPE_QUERY_PARAM);
        final String uuid = query.get(UrlParameters.DOC_UUID_QUERY_PARAM);
        if (type == null || uuid == null || !type.matches(DOC_TYPE_PATTERN) || !uuid.matches(UUID_PATTERN)) {
            return null;
        }
        return new DocRef(type, uuid.toLowerCase());
    }

    /// @param href A link's `href`.
    /// @return True if the link can be opened in a new browser tab: an absolute http, https or mailto link.
    public static boolean isExternal(final String href) {
        if (href == null) {
            return false;
        }
        final String lower = href.trim().toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }

    /// @return The link's query parameters, or null if it has none, or names one more than once (so it cannot be
    /// read two ways).
    private static Map<String, String> query(final String href) {
        final int start = href.indexOf('?');
        if (start < 0) {
            return null;
        }
        final int hash = href.indexOf('#', start);
        final String queryString = hash < 0
                ? href.substring(start + 1)
                : href.substring(start + 1, hash);
        final Map<String, String> params = new HashMap<>();
        for (final String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            final int equals = pair.indexOf('=');
            final String name = equals < 0
                    ? pair
                    : pair.substring(0, equals);
            final String value = equals < 0
                    ? ""
                    : pair.substring(equals + 1);
            if (params.put(name, value) != null) {
                return null;
            }
        }
        return params;
    }
}
