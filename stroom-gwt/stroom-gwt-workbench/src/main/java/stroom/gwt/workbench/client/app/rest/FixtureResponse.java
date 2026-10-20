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

import com.google.gwt.http.client.Header;
import com.google.gwt.http.client.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The response to a request, created from a [RestReply] of kind [RestReply.Kind#RESPONSE].
final class FixtureResponse extends Response {

    private final RestReply reply;

    /// @param reply The reply.
    FixtureResponse(final RestReply reply) {
        this.reply = reply;
    }

    /// @param header A header name, in any case.
    /// @return The reply's header, or null if it hasn't got it.
    @Override
    public String getHeader(final String header) {
        return reply.getHeader(header);
    }

    /// @return The content type and the reply's other headers.
    @Override
    public Header[] getHeaders() {
        final List<Header> headers = new ArrayList<>();
        headers.add(new FixtureHeader(RestReply.CONTENT_TYPE, reply.getContentType()));
        for (final Map.Entry<String, String> entry : reply.getHeaders().entrySet()) {
            headers.add(new FixtureHeader(entry.getKey(), entry.getValue()));
        }
        return headers.toArray(new Header[0]);
    }

    /// @return The headers as `Name: value` lines.
    @Override
    public String getHeadersAsString() {
        final StringBuilder sb = new StringBuilder();
        for (final Header header : getHeaders()) {
            sb.append(header.getName()).append(": ").append(header.getValue()).append("\r\n");
        }
        return sb.toString();
    }

    /// @return The reply's status code.
    @Override
    public int getStatusCode() {
        return reply.getStatus();
    }

    /// @return The standard text of the status code, e.g. `Not Found`.
    @Override
    public String getStatusText() {
        return statusText(reply.getStatus());
    }

    /// @return The reply's body.
    @Override
    public String getText() {
        return reply.getBody();
    }

    /// @param status An HTTP status code.
    /// @return Its standard text, or a generic one.
    static String statusText(final int status) {
        switch (status) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 302:
                return "Found";
            case 400:
                return "Bad Request";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 409:
                return "Conflict";
            case 500:
                return "Internal Server Error";
            case 503:
                return "Service Unavailable";
            default:
                return status < 400
                        ? "OK"
                        : "Error";
        }
    }

    // --------------------------------------------------------------------------------


    /// A header of a [FixtureResponse].
    private static final class FixtureHeader extends Header {

        private final String name;
        private final String value;

        private FixtureHeader(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        /// @return The header's name.
        @Override
        public String getName() {
            return name;
        }

        /// @return The header's value.
        @Override
        public String getValue() {
            return value;
        }
    }
}
