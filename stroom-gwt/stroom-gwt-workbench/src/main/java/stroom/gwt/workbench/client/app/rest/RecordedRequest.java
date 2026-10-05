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

package stroom.gwt.workbench.client.app.rest;

import java.util.Objects;

/// A REST request made by a screen, as seen by the [FixtureDispatcher], e.g.
/// `POST /explorer/v2/find {"filter": ...}`.
public final class RecordedRequest {

    private final String method;
    private final String path;
    private final String query;
    private final String body;

    /// @param method The HTTP method, e.g. `POST`.
    /// @param path   The path relative to the REST service root, e.g. `/explorer/v2/find`.
    /// @param query  The query string without the `?`, or null if there isn't one.
    /// @param body   The request body, or null if there isn't one.
    public RecordedRequest(final String method, final String path, final String query, final String body) {
        this.method = Objects.requireNonNull(method);
        this.path = Objects.requireNonNull(path);
        this.query = query;
        this.body = body;
    }

    /// Creates a request from the URL that RestyGWT sends it to.
    ///
    /// @param method The HTTP method, e.g. `POST`.
    /// @param url    The full URL, e.g. `http://localhost:6008/api/explorer/v2/find?x=1`.
    /// @param body   The request body, or null if there isn't one.
    /// @return The request, with its path relative to the `/api` service root.
    public static RecordedRequest fromUrl(final String method, final String url, final String body) {
        String path = url;
        final int schemeEnd = path.indexOf("//");
        if (schemeEnd >= 0) {
            final int pathStart = path.indexOf('/', schemeEnd + 2);
            path = pathStart >= 0
                    ? path.substring(pathStart)
                    : "/";
        }
        String query = null;
        final int queryStart = path.indexOf('?');
        if (queryStart >= 0) {
            query = path.substring(queryStart + 1);
            path = path.substring(0, queryStart);
        }
        if (path.startsWith(RestFixtures.SERVICE_ROOT + "/")) {
            path = path.substring(RestFixtures.SERVICE_ROOT.length());
        }
        return new RecordedRequest(method.toUpperCase(), path, query, body);
    }

    /// @return The HTTP method, e.g. `POST`.
    public String getMethod() {
        return method;
    }

    /// @return The path relative to the REST service root, e.g. `/explorer/v2/find`.
    public String getPath() {
        return path;
    }

    /// @return The query string without the `?`, or null if there isn't one.
    public String getQuery() {
        return query;
    }

    /// @return The request body, or null if there isn't one.
    public String getBody() {
        return body;
    }

    /// @return The method and path, e.g. `POST /explorer/v2/find`.
    public String describe() {
        return method + " " + path + (query != null
                ? "?" + query
                : "");
    }

    @Override
    public String toString() {
        return describe();
    }
}
