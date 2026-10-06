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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/// The replies of [RestFixtures] for one rendering of a story: it keeps how many requests each
/// route has replied to, so that sequences of replies advance, and starts afresh each time the
/// story renders (as each harness has its own session).
///
/// [#exchange(RecordedRequest)] never throws: an unmatched request gets a `404` reply and a
/// failing fixture a `500` reply, each with a problem for the harness to report.
public final class FixtureSession {

    private final RestFixtures fixtures;
    private final Map<Integer, Integer> callCounts = new HashMap<>();

    /// @param fixtures The fixtures.
    FixtureSession(final RestFixtures fixtures) {
        this.fixtures = Objects.requireNonNull(fixtures);
    }

    /// Finds the reply to a request.
    ///
    /// @param request The request.
    /// @return The reply and any problem, e.g. that no route matched.
    public Exchange exchange(final RecordedRequest request) {
        final int index = fixtures.findRoute(request);
        if (index < 0) {
            final String problem = "No fixture for " + request.describe()
                    + ". Add a route for it to the story's RestFixtures (or use RestFixtures.Builder.lenient() if "
                    + "the story expects it to fail).";
            return new Exchange(request, RestReply.error(404, "No fixture for " + request.describe()),
                    null, problem, false);
        }
        final RestFixtures.Route route = fixtures.getRoute(index);
        final int callIndex = callCounts.getOrDefault(index, 0);
        callCounts.put(index, callIndex + 1);
        try {
            return new Exchange(request, route.reply(request, callIndex), route.getMatcher().describe(), null,
                    false);
        } catch (final RuntimeException e) {
            final String problem = "The fixture for " + route.getMatcher().describe() + " failed on "
                    + request.describe() + ": " + e;
            return new Exchange(request, RestReply.error(500, problem), route.getMatcher().describe(), problem,
                    true);
        }
    }

    /// @return The fixtures.
    public RestFixtures getFixtures() {
        return fixtures;
    }

    // --------------------------------------------------------------------------------


    /// A request and the reply it gets.
    public static final class Exchange {

        private final RecordedRequest request;
        private final RestReply reply;
        private final String route;
        private final String problem;
        private final boolean fixtureFailed;

        private Exchange(final RecordedRequest request,
                         final RestReply reply,
                         final String route,
                         final String problem,
                         final boolean fixtureFailed) {
            this.request = request;
            this.reply = reply;
            this.route = route;
            this.problem = problem;
            this.fixtureFailed = fixtureFailed;
        }

        /// @return The request.
        public RecordedRequest getRequest() {
            return request;
        }

        /// @return The reply, never null.
        public RestReply getReply() {
            return reply;
        }

        /// @return The route that matched, e.g. `POST /explorer/v2/find`, or null if none did.
        public String getRoute() {
            return route;
        }

        /// @return True if a route matched the request.
        public boolean isHandled() {
            return route != null;
        }

        /// @return True if the matching route's handler threw an exception or returned null.
        public boolean isFixtureFailed() {
            return fixtureFailed;
        }

        /// @return What went wrong, for the harness to report, or null if nothing did.
        public String getProblem() {
            return problem;
        }
    }
}
