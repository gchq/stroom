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

    @Test
    void testReply() {
        final RestFixtures fixtures = RestFixtures.builder()
                .post("/explorer/v2/find", FIND)
                .get("/sessionInfo/v1", SESSION)
                .build();

        assertThat(fixtures.reply(post("/explorer/v2/find"))).isSameAs(FIND);
        assertThat(fixtures.reply(get("/sessionInfo/v1"))).isSameAs(SESSION);
    }

    @Test
    void testReply_wrongMethod() {
        final RestFixtures fixtures = RestFixtures.builder()
                .post("/explorer/v2/find", FIND)
                .build();

        final RestReply reply = fixtures.reply(get("/explorer/v2/find"));

        assertThat(reply.getStatus()).isEqualTo(404);
        assertThat(reply.getBody()).contains("No fixture for GET /explorer/v2/find");
        assertThat(fixtures.isHandled(get("/explorer/v2/find"))).isFalse();
    }

    @Test
    void testReply_noMatch() {
        final RestReply reply = RestFixtures.none().reply(get("/sessionInfo/v1"));

        assertThat(reply.getStatus()).isEqualTo(404);
        assertThat(reply.getContentType()).isEqualTo(RestReply.JSON);
    }

    @Test
    void testReply_firstMatchWins() {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/sessionInfo/v1", SESSION)
                .route("*", "/sessionInfo/*", request -> FIND)
                .build();

        assertThat(fixtures.reply(get("/sessionInfo/v1"))).isSameAs(SESSION);
        assertThat(fixtures.reply(post("/sessionInfo/v1"))).isSameAs(FIND);
        assertThat(fixtures.reply(get("/sessionInfo/v2/other"))).isSameAs(FIND);
        assertThat(fixtures.reply(get("/sessionInfoX")).getStatus()).isEqualTo(404);
    }

    @Test
    void testReply_handlerSeesRequest() {
        final RestFixtures fixtures = RestFixtures.builder()
                .post("/explorer/v2/find", request -> request.getBody().contains("Feed")
                        ? FIND
                        : SESSION)
                .build();

        assertThat(fixtures.reply(new RecordedRequest("POST", "/explorer/v2/find", null, "{\"type\":\"Feed\"}")))
                .isSameAs(FIND);
        assertThat(fixtures.reply(new RecordedRequest("POST", "/explorer/v2/find", null, "{}")))
                .isSameAs(SESSION);
    }

    @Test
    void testReply_handlerReturnsNull() {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/sessionInfo/v1", request -> null)
                .build();

        assertThatThrownBy(() -> fixtures.reply(get("/sessionInfo/v1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GET /sessionInfo/v1");
    }

    @Test
    void testAddAll() {
        final RestFixtures common = RestFixtures.builder()
                .get("/sessionInfo/v1", SESSION)
                .build();
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/sessionInfo/v1", FIND)
                .addAll(common)
                .put("/x", FIND)
                .delete("/y", FIND)
                .build();

        assertThat(fixtures.reply(get("/sessionInfo/v1"))).isSameAs(FIND);
        assertThat(fixtures.describeRoutes())
                .containsExactly("GET /sessionInfo/v1", "GET /sessionInfo/v1", "PUT /x", "DELETE /y");
    }

    private static RecordedRequest get(final String path) {
        return new RecordedRequest("GET", path, null, null);
    }

    private static RecordedRequest post(final String path) {
        return new RecordedRequest("POST", path, null, "{}");
    }
}
