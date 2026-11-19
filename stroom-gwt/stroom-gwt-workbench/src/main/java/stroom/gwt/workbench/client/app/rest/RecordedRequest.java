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
        this.method = Objects.requireNonNull(method).toUpperCase();
        this.path = Objects.requireNonNull(path);
        this.query = query;
        this.body = body;
    }

    /// Creates a request from the URL that RestyGWT sends it to.
    ///
    /// @param method      The HTTP method, e.g. `POST`.
    /// @param url         The full URL, e.g. `http://localhost:6008/api/explorer/v2/find?x=1`.
    /// @param body        The request body, or null if there isn't one.
    /// @param serviceRoot The REST service root that RestyGWT prefixes paths with, i.e.
    ///                    `Defaults.getServiceRoot()`, e.g. `http://localhost:6008/api/`. Its path
    ///                    (`/api`) is removed from the start of the URL's path. May be null.
    /// @return The request, with its path relative to the service root.
    public static RecordedRequest fromUrl(final String method,
                                          final String url,
                                          final String body,
                                          final String serviceRoot) {
        String path = pathOf(url);
        String query = null;
        final int queryStart = path.indexOf('?');
        if (queryStart >= 0) {
            query = path.substring(queryStart + 1);
            path = path.substring(0, queryStart);
        }
        return new RecordedRequest(method, stripRoot(path, rootPathOf(serviceRoot)), query, body);
    }

    /// Parses a request as [#describeWithBody()] describes it, e.g. as recorded by the harness's
    /// request spy: `METHOD /path?query body`, where the query and body are optional. The path
    /// and query (sent URL encoded) never hold a space, so the body is everything after the first
    /// space following the path.
    ///
    /// @param described The request as [#describeWithBody()] describes it.
    /// @return The request.
    /// @throws IllegalArgumentException If the text has no method and path.
    public static RecordedRequest parse(final String described) {
        Objects.requireNonNull(described, "described");
        final int methodEnd = described.indexOf(' ');
        if (methodEnd <= 0 || methodEnd + 1 >= described.length()) {
            throw new IllegalArgumentException("Not a request: '" + described + "'");
        }
        final String method = described.substring(0, methodEnd);
        final int pathEnd = described.indexOf(' ', methodEnd + 1);
        final String pathAndQuery = pathEnd >= 0
                ? described.substring(methodEnd + 1, pathEnd)
                : described.substring(methodEnd + 1);
        final String body = pathEnd >= 0
                ? described.substring(pathEnd + 1)
                : null;
        final int queryStart = pathAndQuery.indexOf('?');
        return queryStart >= 0
                ? new RecordedRequest(method, pathAndQuery.substring(0, queryStart),
                pathAndQuery.substring(queryStart + 1), body)
                : new RecordedRequest(method, pathAndQuery, null, body);
    }

    /// @param serviceRoot A service root URL, e.g. `http://localhost:6008/api/`, or null.
    /// @return Its path without a trailing `/`, e.g. `/api`, or an empty string for none.
    static String rootPathOf(final String serviceRoot) {
        if (serviceRoot == null) {
            return "";
        }
        String rootPath = pathOf(serviceRoot);
        final int queryStart = rootPath.indexOf('?');
        if (queryStart >= 0) {
            rootPath = rootPath.substring(0, queryStart);
        }
        while (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length() - 1);
        }
        return rootPath;
    }

    private static String pathOf(final String url) {
        final int schemeEnd = url.indexOf("//");
        if (schemeEnd >= 0) {
            final int pathStart = url.indexOf('/', schemeEnd + 2);
            return pathStart >= 0
                    ? url.substring(pathStart)
                    : "/";
        }
        return url;
    }

    private static String stripRoot(final String path, final String rootPath) {
        if (!rootPath.isEmpty() && path.startsWith(rootPath + "/")) {
            return path.substring(rootPath.length());
        }
        return path;
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

    /// @return The method, path and query, e.g. `GET /node/v1/info?x=1`.
    public String describe() {
        return method + " " + path + (query != null
                ? "?" + query
                : "");
    }

    /// @return [#describe()] followed by the body, if there is one, e.g.
    /// `POST /explorer/v2/find {"filter": ...}`, as recorded by the harness's request spy.
    public String describeWithBody() {
        return describe() + (body != null && !body.isEmpty()
                ? " " + body
                : "");
    }

    /// @return [#describe()].
    @Override
    public String toString() {
        return describe();
    }
}
