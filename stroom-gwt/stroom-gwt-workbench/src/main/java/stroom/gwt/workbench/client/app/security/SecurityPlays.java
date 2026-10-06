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

package stroom.gwt.workbench.client.app.security;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Play helpers shared by the `security` batch's screen stories (the security, identity and
/// document permissions screens).
public final class SecurityPlays {

    private SecurityPlays() {
        // Static utility
    }

    /// Checks the screen reported no errors and made no request without a fixture, as every
    /// screen story ends unless an error is what it shows.
    ///
    /// @param play The play.
    public static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// The requests a request spy recorded that a matcher matches, in the order they were made, e.g.
    /// for checking the first of several searches (React's `rec.finds[0]`). Call it while the
    /// steps run (in a value's supplier), with a spy got while building the play.
    ///
    /// @param requests The rendering's [ScreenHarness#REQUEST_SPY].
    /// @param matcher  The requests to keep.
    /// @return The matching requests.
    public static List<RecordedRequest> requests(final Spy requests, final RequestMatcher matcher) {
        return requests(requests.getCalls(), matcher);
    }

    /// @param calls   A request spy's calls, each a request as [RecordedRequest#describeWithBody()]
    ///                describes it.
    /// @param matcher The requests to keep.
    /// @return The matching requests, in the order of the calls.
    static List<RecordedRequest> requests(final List<List<Object>> calls, final RequestMatcher matcher) {
        final List<RecordedRequest> matching = new ArrayList<>();
        for (final List<Object> call : calls) {
            if (!call.isEmpty() && call.get(0) instanceof final String described) {
                final RecordedRequest request = RecordedRequest.parse(described);
                if (matcher.matches(request)) {
                    matching.add(request);
                }
            }
        }
        return matching;
    }

    /// Narrows a matcher to requests whose JSON body holds exactly the given values once its null
    /// members and empty arrays are left out. RestyGWT doesn't honour `@JsonInclude(NON_NULL)`, so
    /// a change Stroom sends (e.g. `AccountChange`) has every member it leaves alone as `null`, and an
    /// empty action set as `[]`, where React's port leaves them out: e.g. `{"firstName": "Bob"}`
    /// matches `{"userId": null, "firstName": "Bob", "actions": []}`.
    ///
    /// @param matcher      The method and path.
    /// @param expectedJson The members expected to have a value.
    /// @return The narrowed matcher.
    public static RequestMatcher withOnlyValues(final RequestMatcher matcher, final String expectedJson) {
        final Object expected = JsonValues.parse(expectedJson);
        return matcher.withBody("only the values " + expectedJson, body -> {
            if (body == null) {
                return false;
            }
            try {
                return expected.equals(withoutEmptyMembers(JsonValues.parse(body)));
            } catch (final IllegalArgumentException e) {
                return false;
            }
        });
    }

    /// @param value A value returned by [JsonValues#parse(String)].
    /// @return The value, or for an object a copy without its null members and empty arrays.
    static Object withoutEmptyMembers(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final Object member = entry.getValue();
                final boolean empty = member == null
                        || (member instanceof final List<?> list && list.isEmpty());
                if (!empty) {
                    copy.put(entry.getKey(), member);
                }
            }
            return copy;
        }
        return value;
    }

    /// The body of the first request a matcher matches, for checking with a value expectation.
    ///
    /// @param requests The rendering's [ScreenHarness#REQUEST_SPY].
    /// @param matcher  The requests to look at.
    /// @return The first matching request's body, or null if there is no such request.
    public static String firstBody(final Spy requests, final RequestMatcher matcher) {
        return firstBody(requests.getCalls(), matcher);
    }

    /// @param calls   A request spy's calls.
    /// @param matcher The requests to look at.
    /// @return The first matching request's body, or null if there is no such request.
    static String firstBody(final List<List<Object>> calls, final RequestMatcher matcher) {
        final List<RecordedRequest> matching = requests(calls, matcher);
        return matching.isEmpty()
                ? null
                : matching.get(0).getBody();
    }
}
