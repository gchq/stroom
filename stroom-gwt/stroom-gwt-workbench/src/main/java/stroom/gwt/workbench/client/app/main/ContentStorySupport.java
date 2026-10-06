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


package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;

import com.google.gwt.http.client.URL;

import java.util.Map;

/// Helpers shared by the `content` batch's screen stories (`App/Main/*` content, annotation,
/// activity and volume screens).
public final class ContentStorySupport {

    private ContentStorySupport() {
        // Static utility
    }

    /// Adds the checks that the screen showed no alerts and made no request that no fixture
    /// answered, as every screen story ends with.
    ///
    /// @param play The play to add the checks to.
    public static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// Answers the explorer's `decorate` request (`POST /explorer/v2/decorate`), which document
    /// pickers use to check that their document exists, as the server does for a document that
    /// exists: with the request's document.
    ///
    /// @param request The request, a `DecorateRequest`.
    /// @return The reply: the request's `docRef`.
    public static RestReply decorate(final RecordedRequest request) {
        final Map<?, ?> docRef = (Map<?, ?>) ((Map<?, ?>) JsonValues.parse(request.getBody())).get("docRef");
        return RestReply.json("{\"type\": " + jsonString(docRef.get("type"))
                + ", \"uuid\": " + jsonString(docRef.get("uuid"))
                + ", \"name\": " + jsonString(docRef.get("name")) + "}");
    }

    private static String jsonString(final Object value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder("\"");
        for (final char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    /// Gets a parameter of a request's query, decoded.
    ///
    /// @param request The request.
    /// @param name    The parameter's name.
    /// @return The parameter's decoded value, or null if the query has no such parameter.
    public static String queryParam(final RecordedRequest request, final String name) {
        final String query = request.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (final String part : query.split("&")) {
            final int index = part.indexOf('=');
            final String key = index < 0
                    ? part
                    : part.substring(0, index);
            if (name.equals(URL.decodeQueryString(key))) {
                return index < 0
                        ? ""
                        : URL.decodeQueryString(part.substring(index + 1));
            }
        }
        return null;
    }
}
