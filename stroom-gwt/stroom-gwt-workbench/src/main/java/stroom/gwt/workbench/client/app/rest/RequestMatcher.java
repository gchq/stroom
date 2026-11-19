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

import stroom.gwt.workbench.framework.client.play.ValueMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// Which requests a [RestFixtures] route replies to: an HTTP method and a path, optionally
/// narrowed by the query string and the body. Immutable; each `with...` method returns a copy.
/// E.g.
/// ```
/// RequestMatcher.post("/explorer/v2/find").withJsonBody("{\"filter\": {\"nameFilter\": \"Events\"}}")
/// RequestMatcher.get("/node/v1/info").withQuery("node=node1a")
/// RequestMatcher.any("/permission/*")
/// ```
///
/// * The method may be [#ANY_METHOD] to match every method.
/// * A path ending with [#PATH_WILDCARD] matches every path starting with the rest of it.
/// * The query string is ignored unless [#withQuery(String)] or [#withoutQuery()] is used.
/// * The body is ignored unless one of the `withBody`/`withJsonBody` methods is used.
public final class RequestMatcher {

    /// The method that matches every HTTP method.
    public static final String ANY_METHOD = "*";
    /// A path ending with this matches every path starting with the rest of it, e.g.
    /// `/explorer/v2/*`.
    public static final String PATH_WILDCARD = "*";

    private final String method;
    private final String path;
    private final QueryRule queryRule;
    private final List<String> query;
    private final List<BodyRule> bodyRules;

    private RequestMatcher(final String method,
                           final String path,
                           final QueryRule queryRule,
                           final List<String> query,
                           final List<BodyRule> bodyRules) {
        this.method = method;
        this.path = path;
        this.queryRule = queryRule;
        this.query = query;
        this.bodyRules = bodyRules;
    }

    /// @param method The HTTP method, e.g. `POST`, or [#ANY_METHOD].
    /// @param path   The path relative to the REST service root, e.g. `/explorer/v2/find`, which
    ///               may end with [#PATH_WILDCARD].
    /// @return A matcher of the method and path, ignoring the query string and body.
    public static RequestMatcher of(final String method, final String path) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/") && !PATH_WILDCARD.equals(path)) {
            throw new IllegalArgumentException("The path '" + path + "' must start with '/' and be relative to "
                    + "the REST service root, e.g. '/sessionInfo/v1' not '/api/sessionInfo/v1'");
        }
        return new RequestMatcher(method.toUpperCase(), path, QueryRule.IGNORE, Collections.emptyList(),
                Collections.emptyList());
    }

    /// @param path The path, see [#of(String, String)].
    /// @return A matcher of `GET` requests to the path.
    public static RequestMatcher get(final String path) {
        return of("GET", path);
    }

    /// @param path The path, see [#of(String, String)].
    /// @return A matcher of `POST` requests to the path.
    public static RequestMatcher post(final String path) {
        return of("POST", path);
    }

    /// @param path The path, see [#of(String, String)].
    /// @return A matcher of `PUT` requests to the path.
    public static RequestMatcher put(final String path) {
        return of("PUT", path);
    }

    /// @param path The path, see [#of(String, String)].
    /// @return A matcher of `DELETE` requests to the path.
    public static RequestMatcher delete(final String path) {
        return of("DELETE", path);
    }

    /// @param path The path, see [#of(String, String)].
    /// @return A matcher of requests to the path with any method.
    public static RequestMatcher any(final String path) {
        return of(ANY_METHOD, path);
    }

    /// Only matches requests with exactly these query parameters, in any order, e.g.
    /// `withQuery("a=1&b=2")` matches `?b=2&a=1` but not `?a=1` or `?a=1&b=2&c=3`. The parameters
    /// are compared as sent, i.e. still URL encoded, and empty ones (`a=1&&b=2`) are ignored.
    ///
    /// A query with no parameters, e.g. `withQuery("")`, is the same as [#withoutQuery()]: it
    /// matches a request with no query string or an empty one (`/a` or `/a?`).
    ///
    /// @param query The query string without the `?`.
    /// @return A copy of this matcher with the query rule.
    public RequestMatcher withQuery(final String query) {
        Objects.requireNonNull(query, "query");
        final List<String> params = normaliseQuery(query);
        return params.isEmpty()
                ? withoutQuery()
                : new RequestMatcher(method, path, QueryRule.EXACT, params, bodyRules);
    }

    /// Only matches requests without query parameters, i.e. with no query string or an empty one
    /// (`/a` or `/a?`).
    ///
    /// @return A copy of this matcher with the query rule.
    public RequestMatcher withoutQuery() {
        return new RequestMatcher(method, path, QueryRule.NONE, Collections.emptyList(), bodyRules);
    }

    /// Only matches requests whose body is the same JSON value as this, ignoring white space and
    /// the order of object members.
    ///
    /// @param json The JSON.
    /// @return A copy of this matcher with the body rule added.
    public RequestMatcher withJsonBody(final String json) {
        // Fail now rather than never matching
        final Object expected = JsonValues.parse(json);
        return withBodyRule("body = " + json, true, body -> {
            try {
                return body != null && Objects.equals(expected, JsonValues.parse(body));
            } catch (final IllegalArgumentException e) {
                return false;
            }
        });
    }

    /// Only matches requests whose body is JSON containing this JSON, as Jest's `toMatchObject`
    /// compares (see [JsonValues#contains(Object, Object)]): objects need only have the members
    /// given, e.g. `{"criteria": {"jobName": {"string": "Data Retention"}}}` matches a find request
    /// for that job whatever its other members.
    ///
    /// @param json The JSON the body must contain.
    /// @return A copy of this matcher with the body rule added.
    public RequestMatcher withJsonBodyContaining(final String json) {
        // Fail now rather than never matching
        final Object expected = JsonValues.parse(json);
        return withBodyRule("body contains JSON " + json, true, body -> {
            try {
                return body != null && JsonValues.contains(JsonValues.parse(body), expected);
            } catch (final IllegalArgumentException e) {
                return false;
            }
        });
    }

    /// Only matches requests whose body contains this text, e.g. `"\"uuid\":\"d-1\""`.
    ///
    /// @param text The text.
    /// @return A copy of this matcher with the body rule added.
    public RequestMatcher withBodyContaining(final String text) {
        Objects.requireNonNull(text, "text");
        return withBodyRule("body contains " + text, true, body -> body != null && body.contains(text));
    }

    /// Only matches requests whose body satisfies the predicate.
    ///
    /// @param description What the predicate checks, for messages, e.g. `name filter is 'Events'`.
    /// @param predicate   Tests the body, which is null if the request hasn't got one.
    /// @return A copy of this matcher with the body rule added.
    public RequestMatcher withBody(final String description, final Predicate<String> predicate) {
        return withBodyRule(description, false, predicate);
    }

    /// @param description What the predicate checks.
    /// @param exact       True if the description says exactly what the predicate checks, so
    ///                    that two rules with the same description are the same rule.
    /// @param predicate   Tests the body, which is null if the request hasn't got one.
    /// @return A copy of this matcher with the body rule added.
    RequestMatcher withBodyRule(final String description, final boolean exact, final Predicate<String> predicate) {
        final List<BodyRule> newRules = new ArrayList<>(bodyRules);
        newRules.add(new BodyRule(Objects.requireNonNull(description), exact, Objects.requireNonNull(predicate)));
        return new RequestMatcher(method, path, queryRule, query, Collections.unmodifiableList(newRules));
    }

    /// Whether this matcher, in an earlier route, matches every request that a later route's
    /// matcher does, so the later route can never reply. True if the method, path and query rule
    /// of this cover the other's and either this has no body rules or both have the same rules
    /// (as far as their descriptions tell, so not for rules made with
    /// [#withBody(String, Predicate)]).
    ///
    /// @param later The matcher of a later route.
    /// @return True if the later route is unreachable.
    boolean shadows(final RequestMatcher later) {
        return coversMethod(later) && coversPath(later) && coversQuery(later) && coversBody(later);
    }

    /// @param request A request.
    /// @return True if this matches the request.
    public boolean matches(final RecordedRequest request) {
        return matchesMethod(request) && matchesPath(request) && matchesQuery(request) && matchesBody(request);
    }

    /// A matcher of the calls of the harness's request spy (`ScreenHarness.REQUEST_SPY`) for the
    /// requests this matches, so that a play can check the method, path, query and body of a
    /// request the screen made, e.g.
    /// ```
    /// play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
    ///         RequestMatcher.put("/jobNode/v1/11/schedule")
    ///                 .withJsonBodyContaining("{\"expression\": \"0 /5 * * * ?\"}")
    ///                 .toSpyMatcher());
    /// ```
    /// The spy records each request as [RecordedRequest#describeWithBody()], which this parses
    /// back with [RecordedRequest#parse(String)].
    ///
    /// @return A matcher of request spy calls.
    public ValueMatcher toSpyMatcher() {
        final String description = "Request(" + describe() + ")";
        return new ValueMatcher() {
            @Override
            public boolean matchesValue(final Object value) {
                return value instanceof String && matches(RecordedRequest.parse((String) value));
            }

            @Override
            public String describe() {
                return description;
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    /// @return The method this matches, e.g. `POST`, or [#ANY_METHOD].
    public String getMethod() {
        return method;
    }

    /// @return The path this matches.
    public String getPath() {
        return path;
    }

    /// @return The matcher, e.g. `POST /explorer/v2/find [body contains x]`.
    public String describe() {
        final StringBuilder sb = new StringBuilder(method).append(' ').append(path);
        if (queryRule == QueryRule.EXACT) {
            sb.append('?').append(String.join("&", query));
        } else if (queryRule == QueryRule.NONE) {
            sb.append(" [no query]");
        }
        for (final BodyRule rule : bodyRules) {
            sb.append(" [").append(rule.description).append(']');
        }
        return sb.toString();
    }

    /// @return [#describe()].
    @Override
    public String toString() {
        return describe();
    }

    private boolean coversMethod(final RequestMatcher later) {
        return ANY_METHOD.equals(method) || method.equals(later.method);
    }

    private boolean coversPath(final RequestMatcher later) {
        if (path.endsWith(PATH_WILDCARD)) {
            return later.path.startsWith(path.substring(0, path.length() - PATH_WILDCARD.length()));
        }
        return path.equals(later.path);
    }

    private boolean coversQuery(final RequestMatcher later) {
        return queryRule == QueryRule.IGNORE
                || (queryRule == later.queryRule && query.equals(later.query));
    }

    private boolean coversBody(final RequestMatcher later) {
        if (bodyRules.isEmpty()) {
            return true;
        }
        if (bodyRules.size() != later.bodyRules.size()) {
            return false;
        }
        for (int i = 0; i < bodyRules.size(); i++) {
            final BodyRule rule = bodyRules.get(i);
            final BodyRule laterRule = later.bodyRules.get(i);
            if (!rule.exact || !laterRule.exact || !rule.description.equals(laterRule.description)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesMethod(final RecordedRequest request) {
        return ANY_METHOD.equals(method) || method.equals(request.getMethod());
    }

    private boolean matchesPath(final RecordedRequest request) {
        if (path.endsWith(PATH_WILDCARD)) {
            return request.getPath().startsWith(path.substring(0, path.length() - PATH_WILDCARD.length()));
        }
        return path.equals(request.getPath());
    }

    private boolean matchesQuery(final RecordedRequest request) {
        switch (queryRule) {
            case EXACT:
                return request.getQuery() != null && query.equals(normaliseQuery(request.getQuery()));
            case NONE:
                return request.getQuery() == null || normaliseQuery(request.getQuery()).isEmpty();
            default:
                return true;
        }
    }

    private boolean matchesBody(final RecordedRequest request) {
        for (final BodyRule rule : bodyRules) {
            if (!rule.predicate.test(request.getBody())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> normaliseQuery(final String query) {
        final List<String> params = new ArrayList<>();
        for (final String param : Arrays.asList(query.split("&"))) {
            if (!param.isEmpty()) {
                params.add(param);
            }
        }
        Collections.sort(params);
        return Collections.unmodifiableList(params);
    }

    // --------------------------------------------------------------------------------


    private enum QueryRule {
        IGNORE,
        EXACT,
        NONE
    }

    // --------------------------------------------------------------------------------


    private static final class BodyRule {

        private final String description;
        private final boolean exact;
        private final Predicate<String> predicate;

        private BodyRule(final String description, final boolean exact, final Predicate<String> predicate) {
            this.description = description;
            this.exact = exact;
            this.predicate = predicate;
        }
    }
}
