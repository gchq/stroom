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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The fake REST API of a story: the replies to the requests a screen makes, the GWT equivalent
/// of the fixture `api` objects passed to the React screen stories (or of an msw handler list),
/// e.g.
/// ```
/// RestFixtures.builder()
///         .post("/explorer/v2/find", RestReply.json("{\"values\": []}"))
///         .get("/sessionInfo/v1", request -> RestReply.json(SESSION_INFO))
///         .build();
/// ```
///
/// Paths are relative to Stroom's REST service root (`/api`) and may end with `*` to match any
/// path starting with the rest. The first route that matches a request replies to it. A query
/// string in the request is ignored when matching.
public final class RestFixtures {

    /// The root of Stroom's REST API, as set by `RestFactoryImpl`.
    public static final String SERVICE_ROOT = "/api";

    private static final String ANY = "*";
    private static final String WILDCARD = "*";

    private final List<Route> routes;

    private RestFixtures(final List<Route> routes) {
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
    }

    /// @return A builder for the fixtures.
    public static Builder builder() {
        return new Builder();
    }

    /// @return Fixtures that reply to nothing, so every request fails with `404`.
    public static RestFixtures none() {
        return new RestFixtures(Collections.emptyList());
    }

    /// Finds the reply to a request.
    ///
    /// @param request The request.
    /// @return The reply of the first route that matches the request, or a `404` reply if none
    /// does.
    public RestReply reply(final RecordedRequest request) {
        for (final Route route : routes) {
            if (route.matches(request)) {
                final RestReply reply = route.handler.reply(request);
                if (reply == null) {
                    throw new IllegalStateException("The fixture for " + route + " returned null");
                }
                return reply;
            }
        }
        return RestReply.error(404, "No fixture for " + request.describe());
    }

    /// @param request The request.
    /// @return True if a route matches the request.
    public boolean isHandled(final RecordedRequest request) {
        for (final Route route : routes) {
            if (route.matches(request)) {
                return true;
            }
        }
        return false;
    }

    /// @return The routes, as `METHOD path`, in the order they are matched.
    public List<String> describeRoutes() {
        final List<String> list = new ArrayList<>();
        for (final Route route : routes) {
            list.add(route.toString());
        }
        return list;
    }

    // --------------------------------------------------------------------------------


    /// Builds [RestFixtures].
    public static final class Builder {

        private final List<Route> routes = new ArrayList<>();

        private Builder() {
        }

        /// @param path  The path, e.g. `/sessionInfo/v1`.
        /// @param reply The reply.
        /// @return This builder.
        public Builder get(final String path, final RestReply reply) {
            return route("GET", path, request -> reply);
        }

        /// @param path    The path, e.g. `/sessionInfo/v1`.
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder get(final String path, final RestHandler handler) {
            return route("GET", path, handler);
        }

        /// @param path  The path, e.g. `/explorer/v2/find`.
        /// @param reply The reply.
        /// @return This builder.
        public Builder post(final String path, final RestReply reply) {
            return route("POST", path, request -> reply);
        }

        /// @param path    The path, e.g. `/explorer/v2/find`.
        /// @param handler Creates the reply from the request, e.g. depending on its body.
        /// @return This builder.
        public Builder post(final String path, final RestHandler handler) {
            return route("POST", path, handler);
        }

        /// @param path  The path, e.g. `/explorer/v2/find`.
        /// @param reply The reply.
        /// @return This builder.
        public Builder put(final String path, final RestReply reply) {
            return route("PUT", path, request -> reply);
        }

        /// @param path  The path.
        /// @param reply The reply.
        /// @return This builder.
        public Builder delete(final String path, final RestReply reply) {
            return route("DELETE", path, request -> reply);
        }

        /// @param method  The HTTP method, e.g. `POST`, or `*` for any.
        /// @param path    The path, which may end with `*` to match any path starting with the rest.
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder route(final String method, final String path, final RestHandler handler) {
            routes.add(new Route(method.toUpperCase(), path, handler));
            return this;
        }

        /// Adds all the routes of other fixtures, after the routes already added, e.g. to share
        /// the fixtures that every screen needs.
        ///
        /// @param fixtures The fixtures.
        /// @return This builder.
        public Builder addAll(final RestFixtures fixtures) {
            routes.addAll(fixtures.routes);
            return this;
        }

        /// @return The fixtures.
        public RestFixtures build() {
            return new RestFixtures(routes);
        }
    }

    // --------------------------------------------------------------------------------


    private static final class Route {

        private final String method;
        private final String path;
        private final RestHandler handler;

        private Route(final String method, final String path, final RestHandler handler) {
            this.method = Objects.requireNonNull(method);
            this.path = Objects.requireNonNull(path);
            this.handler = Objects.requireNonNull(handler);
        }

        private boolean matches(final RecordedRequest request) {
            if (!ANY.equals(method) && !method.equals(request.getMethod())) {
                return false;
            }
            if (path.endsWith(WILDCARD)) {
                return request.getPath().startsWith(path.substring(0, path.length() - 1));
            }
            return path.equals(request.getPath());
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
