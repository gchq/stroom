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

/// The key of a request in the gwt-suite corpus of the React repository
/// (`gwt-suite/corpus/api/manifest.json`, written by `gwt-suite/lib/corpus.mjs`), e.g.
/// `POST /api/explorer/v2/find #0123456789abcdef`: the method, the path with the `/api` root and
/// any query string, then `#` and the first 16 hex digits of the SHA-1 of the request body (of
/// an empty string if there is no body).
///
/// The key is turned into a [RequestMatcher] for [RestFixtures.Builder#recorded]: the root is
/// removed, the query string must match and, if the key has a body hash, so must the hash of the
/// request's body (see [#bodyHash(String)]). Many endpoints are recorded with several bodies, e.g.
/// one `checkDocumentPermission` key per document, so the hash is what tells them apart.
public final class CorpusKey {

    /// The root that the corpus's paths start with.
    public static final String CORPUS_ROOT = "/api";

    private static final int HASH_LENGTH = 16;

    private final String method;
    private final String path;
    private final String query;
    private final String bodyHash;

    private CorpusKey(final String method, final String path, final String query, final String bodyHash) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.bodyHash = bodyHash;
    }

    /// @param key A corpus key, e.g. `GET /api/sessionInfo/v1 #da39a3ee5e6b4b0d`. The hash part
    ///            is optional.
    /// @return The parsed key.
    /// @throws IllegalArgumentException If it isn't a corpus key.
    public static CorpusKey parse(final String key) {
        Objects.requireNonNull(key, "key");
        String rest = key.trim();
        String bodyHash = null;
        final int hashStart = rest.indexOf(" #");
        if (hashStart >= 0) {
            bodyHash = rest.substring(hashStart + 2).trim();
            rest = rest.substring(0, hashStart).trim();
            if (!isHex(bodyHash)) {
                throw new IllegalArgumentException("Not a corpus key (the body hash should be hex digits): " + key);
            }
        }
        final int space = rest.indexOf(' ');
        if (space <= 0) {
            throw new IllegalArgumentException("Not a corpus key (expected 'METHOD /api/path #hash'): " + key);
        }
        final String method = rest.substring(0, space).toUpperCase();
        String path = rest.substring(space + 1).trim();
        String query = null;
        final int queryStart = path.indexOf('?');
        if (queryStart >= 0) {
            query = path.substring(queryStart + 1);
            path = path.substring(0, queryStart);
        }
        if (path.startsWith(CORPUS_ROOT + "/")) {
            path = path.substring(CORPUS_ROOT.length());
        } else {
            throw new IllegalArgumentException("Not a corpus key (the path should start with " + CORPUS_ROOT
                    + "/): " + key);
        }
        return new CorpusKey(method, path, query, bodyHash);
    }

    /// @return A matcher of the method, path (without the root), exact query string and, if the
    /// key has one, the body hash.
    public RequestMatcher toMatcher() {
        final RequestMatcher matcher = toMatcherIgnoringBody();
        if (bodyHash == null) {
            return matcher;
        }
        final String expectedHash = bodyHash.toLowerCase();
        return matcher.withBodyRule("body #" + expectedHash, true,
                body -> fullHash(body).startsWith(expectedHash));
    }

    /// @return A matcher of the method, path (without the root) and exact query string, ignoring
    /// the body hash, e.g. for a request whose body holds an id the client generates (such as a
    /// search's query key), so that its hash is different every time.
    public RequestMatcher toMatcherIgnoringBody() {
        final RequestMatcher matcher = RequestMatcher.of(method, path);
        return query != null
                ? matcher.withQuery(query)
                : matcher.withoutQuery();
    }

    /// Hashes a request body as the corpus does in its keys.
    ///
    /// @param body The request body, or null if there isn't one (hashed as an empty string).
    /// @return The first 16 hex digits of the SHA-1 of the body as UTF-8, e.g.
    /// `da39a3ee5e6b4b0d` for no body.
    public static String bodyHash(final String body) {
        return fullHash(body).substring(0, HASH_LENGTH);
    }

    private static String fullHash(final String body) {
        return Sha1.hex(body != null
                ? body
                : "");
    }

    private static boolean isHex(final String text) {
        if (text.isEmpty() || text.length() > 40) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /// @return The HTTP method, e.g. `GET`.
    public String getMethod() {
        return method;
    }

    /// @return The path relative to the REST service root, e.g. `/sessionInfo/v1`.
    public String getPath() {
        return path;
    }

    /// @return The query string without the `?`, or null if there isn't one.
    public String getQuery() {
        return query;
    }

    /// @return The body hash, or null if the key hasn't got one.
    public String getBodyHash() {
        return bodyHash;
    }
}
