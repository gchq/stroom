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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSecurityPlays {

    private static final RequestMatcher PUT_ACCOUNT = RequestMatcher.put("/account/v1/1");

    @Test
    void testWithOnlyValues_ignoresNullsAndEmptyArrays() {
        final RequestMatcher matcher = SecurityPlays.withOnlyValues(PUT_ACCOUNT, "{\"firstName\": \"Bob\"}");

        assertThat(matcher.matches(put("{\"userId\":null, \"firstName\":\"Bob\", \"actions\":[]}"))).isTrue();
        assertThat(matcher.matches(put("{\"firstName\":\"Bob\"}"))).isTrue();
    }

    @Test
    void testWithOnlyValues_refusesOtherValues() {
        final RequestMatcher matcher = SecurityPlays.withOnlyValues(PUT_ACCOUNT, "{\"firstName\": \"Bob\"}");

        // Another member with a value
        assertThat(matcher.matches(put("{\"firstName\":\"Bob\", \"email\":\"bob@example.com\"}"))).isFalse();
        // A different value
        assertThat(matcher.matches(put("{\"firstName\":\"Alice\"}"))).isFalse();
        // A non-empty array
        assertThat(matcher.matches(put("{\"firstName\":\"Bob\", \"actions\":[\"DISABLE\"]}"))).isFalse();
        // Another path, no body and a body that isn't JSON
        assertThat(matcher.matches(new RecordedRequest("PUT", "/account/v1/2", null, "{\"firstName\":\"Bob\"}")))
                .isFalse();
        assertThat(matcher.matches(new RecordedRequest("PUT", "/account/v1/1", null, null))).isFalse();
        assertThat(matcher.matches(put("not json"))).isFalse();
    }

    @Test
    void testWithOnlyValues_emptyChange() {
        final RequestMatcher matcher = SecurityPlays.withOnlyValues(PUT_ACCOUNT, "{}");

        assertThat(matcher.matches(put("{\"userId\":null, \"actions\":[]}"))).isTrue();
        assertThat(matcher.matches(put("{\"actions\":[\"UNLOCK\"]}"))).isFalse();
    }

    @Test
    void testWithoutEmptyMembers() {
        final Object value = JsonValues.parse("{\"a\": null, \"b\": [], \"c\": [1], \"d\": \"x\", \"e\": {}}");

        assertThat(SecurityPlays.withoutEmptyMembers(value))
                .isEqualTo(JsonValues.parse("{\"c\": [1], \"d\": \"x\", \"e\": {}}"));
        // Only an object's members are dropped
        assertThat(SecurityPlays.withoutEmptyMembers(JsonValues.parse("[null]")))
                .isEqualTo(JsonValues.parse("[null]"));
        assertThat(SecurityPlays.withoutEmptyMembers(null)).isNull();
        assertThat(SecurityPlays.withoutEmptyMembers(Map.of())).isEqualTo(Map.of());
    }

    @Test
    void testRequestsAndFirstBody() {
        final List<List<Object>> calls = List.of(
                List.of("GET /config/v1/noauth/fetchExtendedUiConfig"),
                List.of("POST /account/v1/search {\"sortList\":[{\"id\":\"userid\"}]}"),
                List.of(),
                List.of("POST /account/v1/search {\"sortList\":[]}"));
        final RequestMatcher search = RequestMatcher.post("/account/v1/search");

        assertThat(SecurityPlays.requests(calls, search))
                .extracting(RecordedRequest::getBody)
                .containsExactly("{\"sortList\":[{\"id\":\"userid\"}]}", "{\"sortList\":[]}");
        assertThat(SecurityPlays.firstBody(calls, search)).isEqualTo("{\"sortList\":[{\"id\":\"userid\"}]}");
        assertThat(SecurityPlays.firstBody(calls, RequestMatcher.put("/account/v1/1"))).isNull();
        assertThat(SecurityPlays.firstBody(List.of(), search)).isNull();
    }

    private static RecordedRequest put(final String body) {
        return new RecordedRequest("PUT", "/account/v1/1", null, body);
    }
}
