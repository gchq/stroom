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

package stroom.gwt.workbench.client.app.screen;

import stroom.gwt.workbench.client.app.rest.FixtureSession;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoryDocumentPlugins {

    private static final String PATH = "/xslt/v1/x1";
    private static final String DOC = "{\"type\": \"XSLT\", \"uuid\": \"x1\", \"name\": \"My XSLT\", \"data\": \"a\"}";

    @Test
    void testDocumentRoutes_fetchRepliesWithTheDocument() {
        final FixtureSession session = StoryDocumentPlugins.documentRoutes(RestFixtures.builder(), PATH, DOC)
                .build()
                .newSession();

        final RestReply reply = session.exchange(new RecordedRequest("GET", PATH, null, "")).getReply();

        assertThat(reply.getStatus()).isEqualTo(200);
        assertThat(reply.getBody()).isEqualTo(DOC);
    }

    @Test
    void testDocumentRoutes_saveEchoesTheDocumentSaved() {
        final FixtureSession session = StoryDocumentPlugins.documentRoutes(RestFixtures.builder(), PATH, DOC)
                .build()
                .newSession();
        final String saved = DOC.replace("\"a\"", "\"b\"");

        final RestReply reply = session.exchange(new RecordedRequest("PUT", PATH, null, saved)).getReply();

        assertThat(reply.getBody()).isEqualTo(saved);
    }

    @Test
    void testDocumentRoutes_leavesOtherDocumentsUnanswered() {
        final FixtureSession session = StoryDocumentPlugins.documentRoutes(RestFixtures.builder(), PATH, DOC)
                .lenient()
                .build()
                .newSession();

        assertThat(session.exchange(new RecordedRequest("GET", "/xslt/v1/x2", null, "")).isHandled()).isFalse();
    }

    @Test
    void testDocumentRoutes_refusesANullDocument() {
        assertThatThrownBy(() -> StoryDocumentPlugins.documentRoutes(RestFixtures.builder(), PATH, null))
                .isInstanceOf(NullPointerException.class);
    }
}
