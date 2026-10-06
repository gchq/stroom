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

package stroom.gwt.workbench.client.app.ai;

import stroom.gwt.workbench.client.app.rest.FixtureSession;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestHandler;
import stroom.gwt.workbench.client.app.rest.RestReply;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiFixtures {

    private static final String QUESTION = "{\"id\": 9, \"messageType\": \"USER_MESSAGE\", \"message\": \"Q\"}";
    private static final String ANSWER = "{\"id\": 10, \"messageType\": \"AI_RESPONSE\", \"message\": \"A\"}";

    @Test
    void testPoll_returnsTheMessagesAfterTheLastSeen() {
        final RestHandler poll = AiFixtures.poll("", true, QUESTION, ANSWER);

        assertThat(ids(poll.reply(pollRequest(0)))).containsExactly(9.0, 10.0);
        assertThat(ids(poll.reply(pollRequest(9)))).containsExactly(10.0);
        assertThat(ids(poll.reply(pollRequest(10)))).isEmpty();
    }

    @Test
    void testPoll_addsTheExtraMembersAndCompletion() {
        final RestHandler poll = AiFixtures.poll("\"attachments\": [{\"id\": 100}]", false, ANSWER);

        final Map<?, ?> reply = (Map<?, ?>) JsonValues.parse(poll.reply(pollRequest(0)).getBody());

        assertThat(reply.get("complete")).isEqualTo(false);
        assertThat((List<?>) reply.get("attachments")).hasSize(1);
    }

    @Test
    void testPoll_withNoBodyReturnsEveryMessage() {
        final RestHandler poll = AiFixtures.poll("", true, ANSWER);

        final RestReply reply = poll.reply(new RecordedRequest("POST", "/ai/v1/pollMessages/1", null, ""));

        assertThat(ids(reply)).containsExactly(10.0);
    }

    @Test
    void testBuilder_answersTheChatsRequests() {
        final FixtureSession session = AiFixtures.builder(AiFixtures.poll("", true)).build().newSession();

        assertThat(session.exchange(post("/ai/v1/createChat", "")).getReply().getBody()).contains("\"id\": 1");
        assertThat(session.exchange(post("/ai/v1/getDefaultConfig", "")).getReply().getBody())
                .isEqualTo(AiFixtures.CONFIG);
        assertThat(session.exchange(post("/preferences/v1", "{}")).isHandled()).isTrue();
        assertThat(session.exchange(post("/ai/v1/cancelProcessing/1", "")).getReply().getBody()).isEqualTo("true");
    }

    @Test
    void testChatRoutes_answersAChatOpenedWithAContext() {
        final FixtureSession session = AiFixtures.chatRoutes(RestFixtures.builder()).build().newSession();

        assertThat(session.exchange(post("/ai/v1/getDefaultConfig", "")).getReply().getBody())
                .isEqualTo(AiFixtures.CONFIG);
        assertThat(session.exchange(post("/preferences/v1", "{}")).getReply().getBody()).isEqualTo("true");
        assertThat(session.exchange(post("/ai/v1/createChat", "")).getReply().getBody()).contains("\"id\": 1");
        assertThat(session.exchange(post("/ai/v1/askStroomAi", "{\"context\": {}}")).isHandled()).isTrue();
        assertThat(session.exchange(post("/ai/v1/updateChatTitle/1", "\"t\"")).getReply().getBody())
                .isEqualTo("true");
        final Map<?, ?> poll = (Map<?, ?>) JsonValues.parse(
                session.exchange(pollRequest(0)).getReply().getBody());
        assertThat(poll.get("complete")).isEqualTo(true);
        assertThat((List<?>) poll.get("newMessages")).isEmpty();
    }

    @Test
    void testChatRoutes_leavesOtherChatsUnanswered() {
        final FixtureSession session = AiFixtures.chatRoutes(RestFixtures.builder()).lenient().build().newSession();

        assertThat(session.exchange(post("/ai/v1/pollMessages/2", "{}")).isHandled()).isFalse();
    }

    private static RecordedRequest pollRequest(final int lastSeenMessageId) {
        return post("/ai/v1/pollMessages/1", "{\"lastSeenMessageId\": " + lastSeenMessageId + "}");
    }

    private static RecordedRequest post(final String path, final String body) {
        return new RecordedRequest("POST", path, null, body);
    }

    private static List<Double> ids(final RestReply reply) {
        final Map<?, ?> json = (Map<?, ?>) JsonValues.parse(reply.getBody());
        return ((List<?>) json.get("newMessages")).stream()
                .map(message -> ((Number) ((Map<?, ?>) message).get("id")).doubleValue())
                .toList();
    }
}
