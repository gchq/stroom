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

/// The fake response to a REST request: a status code and a JSON (or text) body, in the same
/// form as the responses recorded in the gwt-suite corpus of the React repository.
public final class RestReply {

    /// The content type of JSON replies.
    public static final String JSON = "application/json";

    private final int status;
    private final String body;
    private final String contentType;
    private final int delayMillis;

    private RestReply(final int status, final String body, final String contentType, final int delayMillis) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
        this.delayMillis = delayMillis;
    }

    /// @param json The JSON body.
    /// @return A `200 OK` reply with the body.
    public static RestReply json(final String json) {
        return new RestReply(200, json, JSON, 0);
    }

    /// @param status  The HTTP status code, e.g. `500`.
    /// @param message The error message, returned as Stroom's error JSON `{"message": ...}`.
    /// @return An error reply.
    public static RestReply error(final int status, final String message) {
        return new RestReply(status, "{\"code\":" + status + ",\"message\":" + quote(message) + "}", JSON, 0);
    }

    /// @return A `204 No Content` reply, as returned by methods that return nothing.
    public static RestReply noContent() {
        return new RestReply(204, "", JSON, 0);
    }

    /// @param delayMillis How long to wait before replying, e.g. to show a screen loading.
    /// @return A copy of this reply that is delayed.
    public RestReply delayed(final int delayMillis) {
        return new RestReply(status, body, contentType, delayMillis);
    }

    /// @return The HTTP status code.
    public int getStatus() {
        return status;
    }

    /// @return The body.
    public String getBody() {
        return body;
    }

    /// @return The content type of the body.
    public String getContentType() {
        return contentType;
    }

    /// @return How long to wait before replying, in milliseconds.
    public int getDelayMillis() {
        return delayMillis;
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
}
