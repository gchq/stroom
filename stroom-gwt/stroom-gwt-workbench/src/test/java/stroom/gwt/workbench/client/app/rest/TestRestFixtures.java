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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRestFixtures {

    private static final RestReply FIND = RestReply.json("{\"values\":[]}");
    private static final RestReply SESSION = RestReply.json("{\"nodeName\":\"node1a\"}");
    private static final String CHECK_PERMISSION = "/permission/doc/v1/checkDocumentPermission";
    // Request bodies and keys as recorded in gwt-suite/corpus/default
    private static final String DASHBOARD_BODY = "{\"docRef\":{\"type\":\"Dashboard\", "
            + "\"uuid\":\"e177cf16-da6c-4c7d-a19c-09a201f5a2da\", \"name\":\"Test Dashboard\"}, "
            + "\"permission\":\"EDIT\"}";
    private static final String DASHBOARD_KEY = "POST /api" + CHECK_PERMISSION + " #4717b566b9280660";
    private static final String ANNOTATION_BODY = "{\"docRef\":{\"type\":\"Annotation\", "
            + "\"uuid\":\"dc37c707-eaf7-4c52-b3d7-0b5286997f73\", \"name\":\"New Annotation\"}, "
            + "\"permission\":\"EDIT\"}";
    private static final String ANNOTATION_KEY = "POST /api" + CHECK_PERMISSION + " #6789028e7be7fcbb";

    @Test
    void testExchange() {
        final FixtureSession session = RestFixtures.builder()
                .post("/explorer/v2/find", FIND)
                .get("/sessionInfo/v1", SESSION)
                .build()
                .newSession();

        final FixtureSession.Exchange exchange = session.exchange(post("/explorer/v2/find"));
        assertThat(exchange.getReply()).isSameAs(FIND);
        assertThat(exchange.isHandled()).isTrue();
        assertThat(exchange.getRoute()).isEqualTo("POST /explorer/v2/find");
        assertThat(exchange.getProblem()).isNull();
        assertThat(exchange.getRequest().getPath()).isEqualTo("/explorer/v2/find");
        assertThat(session.exchange(get("/sessionInfo/v1")).getReply()).isSameAs(SESSION);
    }

    @Test
    void testExchange_unhandled() {
        final RestFixtures fixtures = RestFixtures.builder()
                .post("/explorer/v2/find", FIND)
                .build();

        final FixtureSession.Exchange exchange = fixtures.newSession().exchange(get("/explorer/v2/find"));

        assertThat(exchange.isHandled()).isFalse();
        assertThat(exchange.getReply().getStatus()).isEqualTo(404);
        assertThat(exchange.getReply().getBody()).contains("No fixture for GET /explorer/v2/find");
        assertThat(exchange.getProblem()).contains("No fixture for GET /explorer/v2/find");
        assertThat(fixtures.isHandled(get("/explorer/v2/find"))).isFalse();
        assertThat(fixtures.isHandled(post("/explorer/v2/find"))).isTrue();
    }

    @Test
    void testStrictByDefault() {
        assertThat(RestFixtures.builder().build().isStrict()).isTrue();
        assertThat(RestFixtures.none().isStrict()).isTrue();
        assertThat(RestFixtures.builder().lenient().build().isStrict()).isFalse();
    }

    @Test
    void testFirstMatchWins() {
        final RestReply specific = RestReply.json("{\"specific\":true}");
        final FixtureSession session = RestFixtures.builder()
                .route(RequestMatcher.post("/find").withBodyContaining("Events"), specific)
                .post("/find", FIND)
                .build()
                .newSession();

        assertThat(session.exchange(new RecordedRequest("POST", "/find", null, "{\"n\":\"Events\"}")).getReply())
                .isSameAs(specific);
        assertThat(session.exchange(new RecordedRequest("POST", "/find", null, "{\"n\":\"x\"}")).getReply())
                .isSameAs(FIND);
    }

    @Test
    void testSequence_lastRepeats() {
        final RestReply first = RestReply.json("1");
        final RestReply second = RestReply.json("2");
        final RestReply last = RestReply.json("3");
        final FixtureSession session = RestFixtures.builder()
                .post("/search", first, second, last)
                .build()
                .newSession();

        assertThat(session.exchange(post("/search")).getReply()).isSameAs(first);
        assertThat(session.exchange(post("/search")).getReply()).isSameAs(second);
        assertThat(session.exchange(post("/search")).getReply()).isSameAs(last);
        assertThat(session.exchange(post("/search")).getReply()).isSameAs(last);
    }

    @Test
    void testSequence_eachSessionStartsAfresh() {
        // Regression: fixtures are static, so the position must belong to the rendering, not them
        final RestReply first = RestReply.json("1");
        final RestReply second = RestReply.json("2");
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/poll", first, second)
                .get("/other", first, second)
                .build();

        final FixtureSession rendering1 = fixtures.newSession();
        assertThat(rendering1.exchange(get("/poll")).getReply()).isSameAs(first);
        assertThat(rendering1.exchange(get("/poll")).getReply()).isSameAs(second);
        // Routes have their own positions
        assertThat(rendering1.exchange(get("/other")).getReply()).isSameAs(first);

        final FixtureSession rendering2 = fixtures.newSession();
        assertThat(rendering2.exchange(get("/poll")).getReply()).isSameAs(first);
    }

    @Test
    void testHandler() {
        final FixtureSession session = RestFixtures.builder()
                .post("/explorer/v2/find", request -> RestReply.json(request.getBody()))
                .put("/doc", request -> RestReply.json("\"put\""))
                .delete("/doc", request -> RestReply.noContent())
                .route("*", "/any/*", request -> RestReply.json("\"" + request.getMethod() + "\""))
                .build()
                .newSession();

        assertThat(session.exchange(new RecordedRequest("POST", "/explorer/v2/find", null, "{\"a\":1}"))
                .getReply().getBody()).isEqualTo("{\"a\":1}");
        assertThat(session.exchange(new RecordedRequest("PUT", "/doc", null, null)).getReply().getBody())
                .isEqualTo("\"put\"");
        assertThat(session.exchange(new RecordedRequest("DELETE", "/doc", null, null)).getReply().getStatus())
                .isEqualTo(204);
        assertThat(session.exchange(new RecordedRequest("PATCH", "/any/thing", null, null)).getReply().getBody())
                .isEqualTo("\"PATCH\"");
    }

    @Test
    void testHandler_failureBecomes500() {
        // Regression: a throwing or null handler used to escape the dispatcher
        final FixtureSession session = RestFixtures.builder()
                .get("/throws", request -> {
                    throw new IllegalStateException("Oops");
                })
                .get("/null", request -> null)
                .build()
                .newSession();

        final FixtureSession.Exchange thrown = session.exchange(get("/throws"));
        assertThat(thrown.isHandled()).isTrue();
        assertThat(thrown.isFixtureFailed()).isTrue();
        assertThat(thrown.getReply().getStatus()).isEqualTo(500);
        assertThat(thrown.getProblem()).contains("GET /throws").contains("Oops");

        final FixtureSession.Exchange nullReply = session.exchange(get("/null"));
        assertThat(nullReply.isFixtureFailed()).isTrue();
        assertThat(nullReply.getProblem()).contains("returned null");
    }

    @Test
    void testRecorded() {
        final FixtureSession session = RestFixtures.builder()
                .recorded("GET /api/sessionInfo/v1 #da39a3ee5e6b4b0d", SESSION)
                .build()
                .newSession();

        assertThat(session.exchange(get("/sessionInfo/v1")).getReply()).isSameAs(SESSION);
        assertThat(session.exchange(new RecordedRequest("GET", "/sessionInfo/v1", "x=1", null)).isHandled())
                .isFalse();
    }

    @Test
    void testRecorded_matchesTheBodyHash() {
        // Regression: the body hash was dropped, so the first recording of an endpoint answered
        // every request to it and the second was never used
        final RestReply allowed = RestReply.json("true");
        final RestReply denied = RestReply.json("false");
        final FixtureSession session = RestFixtures.builder()
                .recorded(DASHBOARD_KEY, allowed)
                .recorded(ANNOTATION_KEY, denied)
                .lenient()
                .build()
                .newSession();

        assertThat(session.exchange(new RecordedRequest("POST", CHECK_PERMISSION, null, ANNOTATION_BODY))
                .getReply()).isSameAs(denied);
        assertThat(session.exchange(new RecordedRequest("POST", CHECK_PERMISSION, null, DASHBOARD_BODY))
                .getReply()).isSameAs(allowed);
        // A body that wasn't recorded matches neither
        assertThat(session.exchange(new RecordedRequest("POST", CHECK_PERMISSION, null, "{}")).isHandled())
                .isFalse();
    }

    @Test
    void testRecorded_sameKeyTwiceIsRefused() {
        final RestFixtures.Builder builder = RestFixtures.builder()
                .recorded(DASHBOARD_KEY, RestReply.json("true"))
                .recorded(DASHBOARD_KEY, RestReply.json("false"));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can never reply")
                .hasMessageContaining("#4717b566b9280660");
    }

    @Test
    void testRecorded_keyWithoutHashShadowsLaterRecordings() {
        final RestFixtures.Builder builder = RestFixtures.builder()
                .recorded("POST /api" + CHECK_PERMISSION, RestReply.json("true"))
                .recorded(ANNOTATION_KEY, RestReply.json("false"));

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testBuild_refusesUnreachableRoutes() {
        // Same method, path and query without a body rule
        assertThatThrownBy(() -> RestFixtures.builder()
                .get("/a", SESSION)
                .get("/a", FIND)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GET /a");
        // A wildcard method and path before a specific route
        assertThatThrownBy(() -> RestFixtures.builder()
                .route("*", "/a/*", request -> SESSION)
                .post("/a/b", FIND)
                .build())
                .isInstanceOf(IllegalStateException.class);
        // The same body rule twice
        assertThatThrownBy(() -> RestFixtures.builder()
                .route(RequestMatcher.post("/a").withJsonBody("{\"x\": 1}"), SESSION)
                .route(RequestMatcher.post("/a").withJsonBody("{\"x\": 1}"), FIND)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testBuild_allowsDistinguishableRoutes() {
        RestFixtures.builder()
                // A more specific route first
                .route(RequestMatcher.post("/a").withBodyContaining("x"), SESSION)
                .post("/a", FIND)
                // Different query rules
                .route(RequestMatcher.get("/q").withQuery("a=1"), SESSION)
                .route(RequestMatcher.get("/q").withQuery("a=2"), SESSION)
                .route(RequestMatcher.get("/q").withoutQuery(), SESSION)
                .get("/q", FIND)
                // Different methods
                .get("/m", SESSION)
                .put("/m", SESSION)
                // Custom body rules can't be compared, so are allowed
                .route(RequestMatcher.post("/c").withBody("x", body -> true), SESSION)
                .route(RequestMatcher.post("/c").withBody("x", body -> false), SESSION)
                .build();
    }

    @Test
    void testBuild_allowsOverridingAddedRoutes() {
        // A story's route overrides a shared one, and shared fixtures may override each other
        final RestFixtures shared = RestFixtures.builder().get("/a", SESSION).build();
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/a", FIND)
                .addAll(shared)
                .addAll(shared)
                .build();

        assertThat(fixtures.newSession().exchange(get("/a")).getReply()).isSameAs(FIND);
    }

    @Test
    void testPutAndDelete_sequences() {
        final RestReply first = RestReply.json("1");
        final RestReply second = RestReply.json("2");
        final FixtureSession session = RestFixtures.builder()
                .put("/doc", first, second)
                .delete("/doc", second, first)
                .build()
                .newSession();
        final RecordedRequest put = new RecordedRequest("PUT", "/doc", null, "{}");
        final RecordedRequest delete = new RecordedRequest("DELETE", "/doc", null, null);

        assertThat(session.exchange(put).getReply()).isSameAs(first);
        assertThat(session.exchange(delete).getReply()).isSameAs(second);
        assertThat(session.exchange(put).getReply()).isSameAs(second);
        assertThat(session.exchange(delete).getReply()).isSameAs(first);
        // The last repeats
        assertThat(session.exchange(put).getReply()).isSameAs(second);
        assertThat(session.exchange(delete).getReply()).isSameAs(first);
        // Only their own methods
        assertThat(session.exchange(post("/doc")).isHandled()).isFalse();
    }

    @Test
    void testFollowedBy_keepsTheStrictnessOfTheStoryFixtures() {
        final RestFixtures startup = StartupFixtures.defaults();
        final RestFixtures strict = RestFixtures.builder().get("/a", SESSION).build();
        final RestFixtures lenient = RestFixtures.builder().get("/a", SESSION).lenient().build();

        assertThat(strict.followedBy(startup).isStrict()).isTrue();
        assertThat(lenient.followedBy(startup).isStrict()).isFalse();
        assertThat(RestFixtures.none().followedBy(startup).isStrict()).isTrue();
        // The fallback's strictness is ignored
        assertThat(strict.followedBy(RestFixtures.builder().lenient().build()).isStrict()).isTrue();
    }

    @Test
    void testFollowedBy_storyRoutesOverrideTheStartupFixtures() {
        final RestReply session = RestReply.json("{\"nodeName\":\"story\"}");
        final RestFixtures fixtures = RestFixtures.builder()
                .get(StartupFixtures.SESSION_INFO_PATH, session)
                .build()
                .followedBy(StartupFixtures.defaults());
        final FixtureSession fixtureSession = fixtures.newSession();

        assertThat(fixtureSession.exchange(get(StartupFixtures.SESSION_INFO_PATH)).getReply()).isSameAs(session);
        // The start-up fixtures answer what the story doesn't
        assertThat(fixtureSession.exchange(get(StartupFixtures.USER_PREFERENCES_PATH)).isHandled()).isTrue();
        assertThat(fixtureSession.exchange(get("/unknown")).isHandled()).isFalse();
        assertThat(fixtures.describeRoutes().get(0)).isEqualTo("GET " + StartupFixtures.SESSION_INFO_PATH);
    }

    @Test
    void testAddAll() {
        final RestFixtures shared = RestFixtures.builder()
                .get("/sessionInfo/v1", SESSION)
                .post("/explorer/v2/find", RestReply.json("{\"shared\":true}"))
                .build();
        final RestFixtures fixtures = RestFixtures.builder()
                .post("/explorer/v2/find", FIND)
                .addAll(shared)
                .build();

        // The story's own routes come first, so they override the shared ones
        assertThat(fixtures.newSession().exchange(post("/explorer/v2/find")).getReply()).isSameAs(FIND);
        assertThat(fixtures.newSession().exchange(get("/sessionInfo/v1")).getReply()).isSameAs(SESSION);
        assertThat(fixtures.describeRoutes())
                .containsExactly("POST /explorer/v2/find", "GET /sessionInfo/v1", "POST /explorer/v2/find");
    }

    private static RecordedRequest get(final String path) {
        return new RecordedRequest("GET", path, null, null);
    }

    private static RecordedRequest post(final String path) {
        return new RecordedRequest("POST", path, null, null);
    }
}
