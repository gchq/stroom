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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRequestMatcher {

    @Test
    void testMethodAndPath() {
        final RequestMatcher matcher = RequestMatcher.post("/explorer/v2/find");

        assertThat(matcher.matches(request("POST", "/explorer/v2/find", null, null))).isTrue();
        assertThat(matcher.matches(request("GET", "/explorer/v2/find", null, null))).isFalse();
        assertThat(matcher.matches(request("POST", "/explorer/v2/find/x", null, null))).isFalse();
        assertThat(matcher.describe()).isEqualTo("POST /explorer/v2/find");
    }

    @Test
    void testAnyMethodAndWildcard() {
        // The method wildcard and the path wildcard are different things
        final RequestMatcher matcher = RequestMatcher.any("/explorer/*");

        assertThat(matcher.getMethod()).isEqualTo(RequestMatcher.ANY_METHOD);
        assertThat(matcher.matches(request("DELETE", "/explorer/v2/delete", null, null))).isTrue();
        assertThat(matcher.matches(request("GET", "/explorer", null, null))).isFalse();
        assertThat(RequestMatcher.get("/explorer/*").matches(request("PUT", "/explorer/x", null, null)))
                .isFalse();
        assertThat(RequestMatcher.put("/a").matches(request("PUT", "/a", null, null))).isTrue();
        assertThat(RequestMatcher.delete("/a").matches(request("DELETE", "/a", null, null))).isTrue();
    }

    @Test
    void testPathMustBeRelativeToRoot() {
        assertThatThrownBy(() -> RequestMatcher.get("sessionInfo/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with '/'");
    }

    @Test
    void testQueryIgnoredByDefault() {
        assertThat(RequestMatcher.get("/node/v1/info").matches(request("GET", "/node/v1/info", "a=1", null)))
                .isTrue();
    }

    @Test
    void testWithQuery() {
        final RequestMatcher matcher = RequestMatcher.get("/node/v1/info").withQuery("a=1&b=2");

        assertThat(matcher.matches(request("GET", "/node/v1/info", "a=1&b=2", null))).isTrue();
        assertThat(matcher.matches(request("GET", "/node/v1/info", "b=2&a=1", null))).isTrue();
        assertThat(matcher.matches(request("GET", "/node/v1/info", "a=1", null))).isFalse();
        assertThat(matcher.matches(request("GET", "/node/v1/info", "a=1&b=2&c=3", null))).isFalse();
        assertThat(matcher.matches(request("GET", "/node/v1/info", null, null))).isFalse();
        assertThat(matcher.describe()).isEqualTo("GET /node/v1/info?a=1&b=2");
    }

    @Test
    void testWithQuery_empty() {
        // No parameters means no query, whether or not there is a '?'
        for (final String empty : new String[]{"", "&", "&&"}) {
            final RequestMatcher matcher = RequestMatcher.get("/a").withQuery(empty);

            assertThat(matcher.matches(request("GET", "/a", null, null))).as(empty).isTrue();
            assertThat(matcher.matches(request("GET", "/a", "", null))).as(empty).isTrue();
            assertThat(matcher.matches(request("GET", "/a", "&", null))).as(empty).isTrue();
            assertThat(matcher.matches(request("GET", "/a", "x=1", null))).as(empty).isFalse();
            assertThat(matcher.describe()).isEqualTo(RequestMatcher.get("/a").withoutQuery().describe());
        }
    }

    @Test
    void testWithQuery_ignoresEmptyParameters() {
        final RequestMatcher matcher = RequestMatcher.get("/a").withQuery("a=1&&b=2&");

        assertThat(matcher.matches(request("GET", "/a", "b=2&a=1", null))).isTrue();
        assertThat(matcher.matches(request("GET", "/a", "&a=1&b=2", null))).isTrue();
    }

    @Test
    void testWithoutQuery() {
        final RequestMatcher matcher = RequestMatcher.get("/a").withoutQuery();

        assertThat(matcher.matches(request("GET", "/a", null, null))).isTrue();
        assertThat(matcher.matches(request("GET", "/a", "", null))).isTrue();
        assertThat(matcher.matches(request("GET", "/a", "x=1", null))).isFalse();
    }

    @Test
    void testWithJsonBody() {
        final RequestMatcher matcher = RequestMatcher.post("/find")
                .withJsonBody("{\"filter\": {\"name\": \"Events\", \"tags\": [\"a\", \"b\"]}, \"n\": 1}");

        // Member order and white space don't matter, nor does 1 versus 1.0
        assertThat(matcher.matches(request("POST", "/find", null,
                "{\"n\":1.0,\"filter\":{\"tags\":[\"a\",\"b\"],\"name\":\"Events\"}}"))).isTrue();
        // Array order does
        assertThat(matcher.matches(request("POST", "/find", null,
                "{\"n\":1,\"filter\":{\"tags\":[\"b\",\"a\"],\"name\":\"Events\"}}"))).isFalse();
        assertThat(matcher.matches(request("POST", "/find", null, "not json"))).isFalse();
        assertThat(matcher.matches(request("POST", "/find", null, null))).isFalse();
    }

    @Test
    void testWithJsonBody_invalidExpectation() {
        assertThatThrownBy(() -> RequestMatcher.post("/find").withJsonBody("{bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWithBodyContainingAndPredicate() {
        final RequestMatcher matcher = RequestMatcher.post("/find")
                .withBodyContaining("Events")
                .withBody("is short", body -> body.length() < 30);

        assertThat(matcher.matches(request("POST", "/find", null, "{\"name\":\"Events\"}"))).isTrue();
        assertThat(matcher.matches(request("POST", "/find", null, "{\"name\":\"Other\"}"))).isFalse();
        assertThat(matcher.matches(request("POST", "/find", null,
                "{\"name\":\"Events\", \"padding\": \"xxxxxxxxxxxxxxxx\"}"))).isFalse();
        assertThat(matcher.describe()).isEqualTo("POST /find [body contains Events] [is short]");
    }

    private static RecordedRequest request(final String method,
                                           final String path,
                                           final String query,
                                           final String body) {
        return new RecordedRequest(method, path, query, body);
    }

    @Test
    void testJsonBodyContaining() {
        final RequestMatcher matcher = RequestMatcher.post("/jobNode/v1/find")
                .withJsonBodyContaining("{\"jobName\": {\"string\": \"Data Retention\"}}");

        assertThat(matcher.matches(request("POST", "/jobNode/v1/find", null,
                "{\"jobName\":{\"string\":\"Data Retention\",\"matchStyle\":null},\"sort\":[]}"))).isTrue();
        assertThat(matcher.matches(request("POST", "/jobNode/v1/find", null,
                "{\"jobName\":{\"string\":\"Other\"}}"))).isFalse();
        assertThat(matcher.matches(request("POST", "/jobNode/v1/find", null, "not json"))).isFalse();
        assertThat(matcher.matches(request("POST", "/jobNode/v1/find", null, null))).isFalse();
        assertThat(matcher.describe()).isEqualTo(
                "POST /jobNode/v1/find [body contains JSON {\"jobName\": {\"string\": \"Data Retention\"}}]");
        assertThatThrownBy(() -> RequestMatcher.post("/a").withJsonBodyContaining("{"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToSpyMatcher() {
        final ValueMatcher matcher = RequestMatcher.put("/jobNode/v1/11/schedule")
                .withJsonBodyContaining("{\"expression\": \"0 /5 * * * ?\"}")
                .toSpyMatcher();

        assertThat(matcher.matchesValue(
                "PUT /jobNode/v1/11/schedule {\"type\":\"CRON\",\"expression\":\"0 /5 * * * ?\"}")).isTrue();
        assertThat(matcher.matchesValue(
                "PUT /jobNode/v1/12/schedule {\"type\":\"CRON\",\"expression\":\"0 /5 * * * ?\"}")).isFalse();
        assertThat(matcher.matchesValue("PUT /jobNode/v1/11/schedule")).isFalse();
        assertThat(matcher.matchesValue(null)).isFalse();
        assertThat(matcher.matchesValue(42)).isFalse();
        assertThat(matcher.describe()).isEqualTo(
                "Request(PUT /jobNode/v1/11/schedule [body contains JSON {\"expression\": \"0 /5 * * * ?\"}])");
    }

    @Test
    void testToSpyMatcherWithQuery() {
        final ValueMatcher matcher = RequestMatcher.get("/jobNode/v1/info")
                .withQuery("jobName=Data%20Retention&nodeName=node2")
                .toSpyMatcher();

        assertThat(matcher.matchesValue("GET /jobNode/v1/info?nodeName=node2&jobName=Data%20Retention")).isTrue();
        assertThat(matcher.matchesValue("GET /jobNode/v1/info?nodeName=node1&jobName=Data%20Retention")).isFalse();
    }
}
