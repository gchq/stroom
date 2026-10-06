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
import java.util.Arrays;
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
///         // A sequence: the first request gets the first reply, and so on; the last repeats
///         .post("/search/v1", RestReply.json(PENDING), RestReply.json(COMPLETE))
///         .route(RequestMatcher.post("/explorer/v2/find").withBodyContaining("Events"),
///                 RestReply.json(EVENTS))
///         // File uploads (Stroom's CustomFileUpload), a sequence as above
///         .upload(UploadReply.success("res-1", "import.zip"))
///         .build();
/// ```
///
/// Paths are relative to Stroom's REST service root (`/api`); see [RequestMatcher] for wildcards
/// and matching the query string or body (both are ignored unless asked for). The first route
/// that matches a request replies to it, so put more specific routes first; [Builder#build()]
/// refuses a route that an earlier one makes unreachable.
///
/// Fixtures are immutable and hold no state, so can be `static final`: the position in a sequence
/// is kept by the [FixtureSession] of each rendering, so a re-rendered story starts its sequences
/// again.
///
/// A request that no route matches gets a `404` reply and is reported by the harness. Fixtures are
/// strict by default, so that also fails the story; use [Builder#lenient()] for a story that
/// expects unmatched requests. The same goes for a file upload that no upload route
/// ([Builder#upload(UploadReply, UploadReply...)]) matches.
public final class RestFixtures {

    private final List<Route> routes;
    private final List<UploadRoute> uploadRoutes;
    private final boolean strict;

    private RestFixtures(final List<Route> routes, final List<UploadRoute> uploadRoutes, final boolean strict) {
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        this.uploadRoutes = Collections.unmodifiableList(new ArrayList<>(uploadRoutes));
        this.strict = strict;
    }

    /// @return A builder for the fixtures.
    public static Builder builder() {
        return new Builder();
    }

    /// @return Strict fixtures that reply to nothing, so every request fails the story.
    public static RestFixtures none() {
        return new RestFixtures(Collections.emptyList(), Collections.emptyList(), true);
    }

    /// Combines these fixtures with ones to fall back on, e.g. a story's fixtures with the
    /// start-up fixtures that every screen needs. These routes come first, so override the
    /// fallback's, and these fixtures' strictness is kept (the fallback's is ignored).
    ///
    /// @param fallback The fixtures for requests that none of these routes match.
    /// @return The combined fixtures.
    public RestFixtures followedBy(final RestFixtures fallback) {
        final List<Route> combined = new ArrayList<>(routes);
        combined.addAll(Objects.requireNonNull(fallback, "fallback").routes);
        final List<UploadRoute> combinedUploads = new ArrayList<>(uploadRoutes);
        combinedUploads.addAll(fallback.uploadRoutes);
        return new RestFixtures(combined, combinedUploads, strict);
    }

    /// @return A new session, which keeps the position in each sequence of replies for one
    /// rendering of a story.
    public FixtureSession newSession() {
        return new FixtureSession(this);
    }

    /// @return True if a request that no route matches should fail the story.
    public boolean isStrict() {
        return strict;
    }

    /// @param request The request.
    /// @return True if a route matches the request.
    public boolean isHandled(final RecordedRequest request) {
        return findRoute(request) >= 0;
    }

    /// @return The routes, e.g. `POST /explorer/v2/find`, in the order they are matched.
    public List<String> describeRoutes() {
        final List<String> list = new ArrayList<>();
        for (final Route route : routes) {
            list.add(route.matcher.describe());
        }
        return list;
    }

    /// @param request The request.
    /// @return The index of the first route that matches the request, or -1 if none does.
    int findRoute(final RecordedRequest request) {
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).matcher.matches(request)) {
                return i;
            }
        }
        return -1;
    }

    /// @param index The index of a route.
    /// @return The route.
    Route getRoute(final int index) {
        return routes.get(index);
    }

    /// @param upload A file upload.
    /// @return The index of the first upload route that matches the upload, or -1 if none does.
    int findUploadRoute(final RecordedUpload upload) {
        for (int i = 0; i < uploadRoutes.size(); i++) {
            if (uploadRoutes.get(i).matches(upload)) {
                return i;
            }
        }
        return -1;
    }

    /// @param index The index of an upload route.
    /// @return The upload route.
    UploadRoute getUploadRoute(final int index) {
        return uploadRoutes.get(index);
    }

    // --------------------------------------------------------------------------------


    /// Builds [RestFixtures].
    public static final class Builder {

        private final List<Route> routes = new ArrayList<>();
        // The routes added with this builder's route methods (not addAll), compared by identity
        private final List<Route> ownRoutes = new ArrayList<>();
        private final List<UploadRoute> uploadRoutes = new ArrayList<>();
        // The upload routes added with this builder's upload methods (not addAll)
        private final List<UploadRoute> ownUploadRoutes = new ArrayList<>();
        private boolean strict = true;

        private Builder() {
        }

        /// @param path  The path, e.g. `/sessionInfo/v1`.
        /// @param reply The reply, or the first of a sequence of replies.
        /// @param more  The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder get(final String path, final RestReply reply, final RestReply... more) {
            return route(RequestMatcher.get(path), reply, more);
        }

        /// @param path    The path, e.g. `/sessionInfo/v1`.
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder get(final String path, final RestHandler handler) {
            return route(RequestMatcher.get(path), handler);
        }

        /// @param path  The path, e.g. `/explorer/v2/find`.
        /// @param reply The reply, or the first of a sequence of replies.
        /// @param more  The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder post(final String path, final RestReply reply, final RestReply... more) {
            return route(RequestMatcher.post(path), reply, more);
        }

        /// @param path    The path, e.g. `/explorer/v2/find`.
        /// @param handler Creates the reply from the request, e.g. depending on its body.
        /// @return This builder.
        public Builder post(final String path, final RestHandler handler) {
            return route(RequestMatcher.post(path), handler);
        }

        /// @param path  The path.
        /// @param reply The reply, or the first of a sequence of replies.
        /// @param more  The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder put(final String path, final RestReply reply, final RestReply... more) {
            return route(RequestMatcher.put(path), reply, more);
        }

        /// @param path    The path.
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder put(final String path, final RestHandler handler) {
            return route(RequestMatcher.put(path), handler);
        }

        /// @param path  The path.
        /// @param reply The reply, or the first of a sequence of replies.
        /// @param more  The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder delete(final String path, final RestReply reply, final RestReply... more) {
            return route(RequestMatcher.delete(path), reply, more);
        }

        /// @param path    The path.
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder delete(final String path, final RestHandler handler) {
            return route(RequestMatcher.delete(path), handler);
        }

        /// @param method  The HTTP method, e.g. `POST`, or [RequestMatcher#ANY_METHOD].
        /// @param path    The path, which may end with [RequestMatcher#PATH_WILDCARD].
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder route(final String method, final String path, final RestHandler handler) {
            return route(RequestMatcher.of(method, path), handler);
        }

        /// @param matcher The requests to reply to, e.g. matching on the body.
        /// @param reply   The reply, or the first of a sequence of replies.
        /// @param more    The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder route(final RequestMatcher matcher, final RestReply reply, final RestReply... more) {
            final List<RestReply> replies = new ArrayList<>();
            replies.add(Objects.requireNonNull(reply, "reply"));
            for (final RestReply next : Arrays.asList(more)) {
                replies.add(Objects.requireNonNull(next, "reply"));
            }
            return addOwn(new Route(Objects.requireNonNull(matcher, "matcher"), replies, null));
        }

        /// @param matcher The requests to reply to, e.g. matching on the body.
        /// @param handler Creates the reply from the request.
        /// @return This builder.
        public Builder route(final RequestMatcher matcher, final RestHandler handler) {
            return addOwn(new Route(Objects.requireNonNull(matcher, "matcher"), null,
                    Objects.requireNonNull(handler, "handler")));
        }

        /// Adds a route for a request recorded in the gwt-suite corpus, keyed as the corpus keys
        /// it, e.g. `POST /api/permission/doc/v1/checkDocumentPermission #4717b566b9280660`. The
        /// `/api` root is removed, the query must match exactly and, if the key has a body hash,
        /// the SHA-1 of the request body must match it, so several recordings of one endpoint with
        /// different bodies each answer their own request. For a request whose body holds an id
        /// the client generates (so its hash differs every time), use
        /// `route(CorpusKey.parse(key).toMatcherIgnoringBody(), ...)` instead. See [CorpusKey].
        ///
        /// @param corpusKey The key of the request in the corpus manifest.
        /// @param reply     The first recorded reply.
        /// @param more      The rest of the recorded replies, in order. The last repeats, as in
        ///                  the corpus.
        /// @return This builder.
        public Builder recorded(final String corpusKey, final RestReply reply, final RestReply... more) {
            return route(CorpusKey.parse(corpusKey).toMatcher(), reply, more);
        }

        /// Adds all the routes of other fixtures, after the routes already added, e.g. to share
        /// the fixtures that every screen needs. Their strictness is ignored. A route added
        /// earlier may override one of theirs (e.g. a story's own session info before the
        /// start-up fixtures'), so they aren't checked for unreachable routes.
        ///
        /// @param fixtures The fixtures.
        /// @return This builder.
        public Builder addAll(final RestFixtures fixtures) {
            routes.addAll(fixtures.routes);
            uploadRoutes.addAll(fixtures.uploadRoutes);
            return this;
        }

        /// Replies to the file uploads a screen makes (Stroom's `CustomFileUpload.submit()`, which
        /// posts the chosen file to the import file servlet), whatever the file, e.g.
        /// `upload(UploadReply.success("res-1", "import.zip"))`.
        ///
        /// @param reply The reply, or the first of a sequence of replies.
        /// @param more  The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder upload(final UploadReply reply, final UploadReply... more) {
            return addOwnUpload(new UploadRoute(null, replies(reply, more)));
        }

        /// Replies to the uploads of a file with the given name, e.g. a successful upload of one
        /// file and a failing upload of another. Put it before any route for all uploads.
        ///
        /// @param fileName The chosen file's name, e.g. `import.zip`.
        /// @param reply    The reply, or the first of a sequence of replies.
        /// @param more     The rest of the sequence. The last reply repeats.
        /// @return This builder.
        public Builder upload(final String fileName, final UploadReply reply, final UploadReply... more) {
            return addOwnUpload(new UploadRoute(Objects.requireNonNull(fileName, "fileName"), replies(reply, more)));
        }

        /// Makes the fixtures lenient: a request that no route matches still gets a `404` reply
        /// and is reported, but doesn't fail the story.
        ///
        /// @return This builder.
        public Builder lenient() {
            strict = false;
            return this;
        }

        /// @return The fixtures.
        /// @throws IllegalStateException If a route added to this builder (other than with
        ///                               [#addAll(RestFixtures)]) can never reply, because an
        ///                               earlier route matches the same requests, e.g. two routes
        ///                               for the same method, path and query without a body rule,
        ///                               or the same corpus key recorded twice.
        public RestFixtures build() {
            for (int later = 0; later < routes.size(); later++) {
                final Route laterRoute = routes.get(later);
                if (!ownRoutes.contains(laterRoute)) {
                    continue;
                }
                for (int earlier = 0; earlier < later; earlier++) {
                    final RequestMatcher earlierMatcher = routes.get(earlier).matcher;
                    if (earlierMatcher.shadows(laterRoute.matcher)) {
                        throw new IllegalStateException("The route " + laterRoute.matcher.describe()
                                + " can never reply, as the earlier route " + earlierMatcher.describe()
                                + " matches the same requests. Remove one, put the more specific route "
                                + "first, or tell them apart with a body rule (e.g. "
                                + "RequestMatcher.withJsonBody) or a reply sequence.");
                    }
                }
            }
            for (int later = 0; later < uploadRoutes.size(); later++) {
                final UploadRoute laterRoute = uploadRoutes.get(later);
                if (!ownUploadRoutes.contains(laterRoute)) {
                    continue;
                }
                for (int earlier = 0; earlier < later; earlier++) {
                    final UploadRoute earlierRoute = uploadRoutes.get(earlier);
                    if (earlierRoute.shadows(laterRoute)) {
                        throw new IllegalStateException("The upload route " + laterRoute.describe()
                                + " can never reply, as the earlier upload route " + earlierRoute.describe()
                                + " matches the same uploads. Remove one, put the route for a file name "
                                + "first, or use a reply sequence.");
                    }
                }
            }
            return new RestFixtures(routes, uploadRoutes, strict);
        }

        private Builder addOwn(final Route route) {
            routes.add(route);
            ownRoutes.add(route);
            return this;
        }

        private Builder addOwnUpload(final UploadRoute route) {
            uploadRoutes.add(route);
            ownUploadRoutes.add(route);
            return this;
        }

        private static List<UploadReply> replies(final UploadReply reply, final UploadReply... more) {
            final List<UploadReply> replies = new ArrayList<>();
            replies.add(Objects.requireNonNull(reply, "reply"));
            for (final UploadReply next : Arrays.asList(more)) {
                replies.add(Objects.requireNonNull(next, "reply"));
            }
            return replies;
        }
    }

    // --------------------------------------------------------------------------------


    /// A route: the requests it matches and either a sequence of replies or a handler.
    static final class Route {

        private final RequestMatcher matcher;
        private final List<RestReply> replies;
        private final RestHandler handler;

        private Route(final RequestMatcher matcher, final List<RestReply> replies, final RestHandler handler) {
            this.matcher = matcher;
            this.replies = replies != null
                    ? Collections.unmodifiableList(replies)
                    : null;
            this.handler = handler;
        }

        /// @param request   The request, which the route matches.
        /// @param callIndex How many earlier requests this route has replied to in the session.
        /// @return The reply, never null.
        /// @throws RuntimeException If the handler fails or returns null.
        RestReply reply(final RecordedRequest request, final int callIndex) {
            if (handler != null) {
                final RestReply reply = handler.reply(request);
                if (reply == null) {
                    throw new IllegalStateException("The fixture for " + matcher.describe() + " returned null");
                }
                return reply;
            }
            return replies.get(Math.min(callIndex, replies.size() - 1));
        }

        /// @return The route's matcher.
        RequestMatcher getMatcher() {
            return matcher;
        }
    }

    // --------------------------------------------------------------------------------


    /// An upload route: the file uploads it matches (all, or those of one file name) and its
    /// sequence of replies.
    static final class UploadRoute {

        private final String fileName;
        private final List<UploadReply> replies;

        private UploadRoute(final String fileName, final List<UploadReply> replies) {
            this.fileName = fileName;
            this.replies = Collections.unmodifiableList(replies);
        }

        /// @param upload An upload.
        /// @return True if this route replies to it.
        boolean matches(final RecordedUpload upload) {
            return fileName == null || fileName.equals(upload.getFileName());
        }

        /// @param later A route after this one.
        /// @return True if this route matches every upload the later one does.
        boolean shadows(final UploadRoute later) {
            return fileName == null || fileName.equals(later.fileName);
        }

        /// @param callIndex How many earlier uploads this route has replied to in the session.
        /// @return The reply.
        UploadReply reply(final int callIndex) {
            return replies.get(Math.min(callIndex, replies.size() - 1));
        }

        /// @return The route, e.g. `upload of import.zip` or `upload of any file`.
        String describe() {
            return fileName != null
                    ? "upload of " + fileName
                    : "upload of any file";
        }
    }
}
