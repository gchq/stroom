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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// The fake response to a REST request: a status code, a body, its content type and any other
/// headers, in the same form as the responses recorded in the gwt-suite corpus of the React
/// repository. It can also be a network failure ([#networkError(String)]) or a timeout
/// ([#timeout()]), which RestyGWT reports through `RequestCallback.onError` rather than as a
/// response. Replies are immutable, so can be shared by stories.
///
/// A `401` reply is refused: Stroom's `RestFactoryImpl` reloads the page on a `401` (it assumes
/// the session has expired), which would reload the workbench forever.
public final class RestReply {

    /// The content type of JSON replies.
    public static final String JSON = "application/json";
    /// The content type of text replies. Stroom's `RestError` reads an error reply as text only if
    /// its content type is exactly this.
    public static final String TEXT = "text/plain";

    /// Why a `401` reply is refused.
    static final String UNAUTHORISED_MESSAGE = "A 401 reply would make Stroom's RestFactoryImpl reload the "
            + "page (it assumes the session has expired), so the story would reload forever. Use a "
            + "403 to model a request the user isn't allowed to make; an expired session can't be "
            + "shown in a story.";

    /// The name of the content type header.
    static final String CONTENT_TYPE = "Content-Type";

    private static final int OK = 200;
    private static final int NO_CONTENT = 204;

    private final Kind kind;
    private final int status;
    private final String body;
    private final String contentType;
    private final Map<String, String> headers;
    private final int delayMillis;

    private RestReply(final Kind kind,
                      final int status,
                      final String body,
                      final String contentType,
                      final Map<String, String> headers,
                      final int delayMillis) {
        this.kind = kind;
        this.status = status;
        this.body = body;
        this.contentType = contentType;
        this.headers = headers;
        this.delayMillis = delayMillis;
    }

    /// @param json The JSON body.
    /// @return A `200 OK` reply with the body.
    public static RestReply json(final String json) {
        return of(OK, JSON, json);
    }

    /// @param status The HTTP status code, e.g. `409`.
    /// @param json   The JSON body.
    /// @return A reply with the status and body.
    public static RestReply json(final int status, final String json) {
        return of(status, JSON, json);
    }

    /// @param text The body.
    /// @return A `200 OK` `text/plain` reply, e.g. for a resource method returning a `String`.
    public static RestReply text(final String text) {
        return of(OK, TEXT, text);
    }

    /// @param status The HTTP status code, e.g. `500`.
    /// @param text   The body.
    /// @return A `text/plain` reply with the status, e.g. an error that Stroom's `RestError`
    /// shows as its text rather than parsing as JSON.
    public static RestReply text(final int status, final String text) {
        return of(status, TEXT, text);
    }

    /// @param status      The HTTP status code, not `401`.
    /// @param contentType The content type, e.g. `application/json`.
    /// @param body        The body, may be empty.
    /// @return A reply, e.g. one copied from the gwt-suite corpus.
    public static RestReply of(final int status, final String contentType, final String body) {
        checkStatus(status);
        return new RestReply(Kind.RESPONSE, status, Objects.requireNonNullElse(body, ""),
                Objects.requireNonNull(contentType, "contentType"), Collections.emptyMap(), 0);
    }

    /// @param status  The HTTP status code, e.g. `500`. Not `401`.
    /// @param message The error message, returned as Stroom's error JSON `{"code": ..., "message": ...}`.
    /// @return An error reply.
    public static RestReply error(final int status, final String message) {
        return of(status, JSON, "{\"code\":" + status + ",\"message\":" + quote(message) + "}");
    }

    /// @return A `204 No Content` reply, as returned by methods that return nothing.
    public static RestReply noContent() {
        return of(NO_CONTENT, JSON, "");
    }

    /// A request that fails without a response, e.g. because the server is unreachable. RestyGWT
    /// calls `RequestCallback.onError` with a `RequestException` holding the message.
    ///
    /// @param message The exception's message.
    /// @return The reply.
    public static RestReply networkError(final String message) {
        return new RestReply(Kind.NETWORK_ERROR, 0, Objects.requireNonNullElse(message, "Network error"),
                TEXT, Collections.emptyMap(), 0);
    }

    /// A request that times out. RestyGWT calls `RequestCallback.onError` with a
    /// `RequestTimeoutException`. Use [#delayed(int)] to set how long it takes to time out.
    ///
    /// @return The reply.
    public static RestReply timeout() {
        return new RestReply(Kind.TIMEOUT, 0, "", TEXT, Collections.emptyMap(), 0);
    }

    /// @param delayMillis How long to wait before replying, e.g. to show a screen loading.
    /// @return A copy of this reply that is delayed.
    public RestReply delayed(final int delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("Negative delay " + delayMillis);
        }
        return new RestReply(kind, status, body, contentType, headers, delayMillis);
    }

    /// @param name  The header's name, in any case, e.g. `Content-Disposition` or `Location`.
    ///              `Content-Type` replaces the reply's content type, as there is only one.
    /// @param value The header's value.
    /// @return A copy of this reply with the header added (or replaced, whatever the case of
    /// its name).
    public RestReply withHeader(final String name, final String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (CONTENT_TYPE.equalsIgnoreCase(name)) {
            return new RestReply(kind, status, body, value, headers, delayMillis);
        }
        final Map<String, String> newHeaders = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(name)) {
                newHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        newHeaders.put(name, value);
        return new RestReply(kind, status, body, contentType, Collections.unmodifiableMap(newHeaders),
                delayMillis);
    }

    /// @return Whether this is a response, a network error or a timeout.
    public Kind getKind() {
        return kind;
    }

    /// @return The HTTP status code, or 0 for a network error or timeout.
    public int getStatus() {
        return status;
    }

    /// @return The body, or for a network error its message.
    public String getBody() {
        return body;
    }

    /// @return The content type of the body.
    public String getContentType() {
        return contentType;
    }

    /// @return The headers other than the content type, in the order they were added.
    public Map<String, String> getHeaders() {
        return headers;
    }

    /// @param name A header name, in any case, e.g. `content-type`.
    /// @return The header's value, or null if the reply hasn't got it.
    public String getHeader(final String name) {
        if (CONTENT_TYPE.equalsIgnoreCase(name)) {
            return contentType;
        }
        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /// @return How long to wait before replying, in milliseconds.
    public int getDelayMillis() {
        return delayMillis;
    }

    /// @return The reply, e.g. `200 application/json` or `network error`.
    @Override
    public String toString() {
        switch (kind) {
            case NETWORK_ERROR:
                return "network error: " + body;
            case TIMEOUT:
                return "timeout";
            default:
                return status + " " + contentType;
        }
    }

    /// @param status An HTTP status code.
    /// @throws IllegalArgumentException If it isn't one, or is `401`.
    static void checkStatus(final int status) {
        if (status == 401) {
            throw new IllegalArgumentException(UNAUTHORISED_MESSAGE);
        }
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Not an HTTP status code: " + status);
        }
    }

    /// @param text Some text.
    /// @return The text as a JSON string.
    static String quote(final String text) {
        if (text == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            final char chr = text.charAt(i);
            if (chr == '"' || chr == '\\') {
                sb.append('\\').append(chr);
            } else if (chr == '\n') {
                sb.append("\\n");
            } else if (chr < ' ') {
                final String hex = Integer.toHexString(chr);
                sb.append("\\u").append("0000".substring(hex.length())).append(hex);
            } else {
                sb.append(chr);
            }
        }
        return sb.append('"').toString();
    }

    // --------------------------------------------------------------------------------


    /// What a reply does.
    public enum Kind {
        /// A response with a status code, e.g. `200` or `500`.
        RESPONSE,
        /// A failure without a response, reported to `RequestCallback.onError`.
        NETWORK_ERROR,
        /// A timeout, reported to `RequestCallback.onError`.
        TIMEOUT
    }
}
