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

package stroom.gwt.workbench.client.app.editors;

import stroom.gwt.workbench.client.app.rest.FixtureSession;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocEditors {

    private static final String FEED = "{\"type\": \"Feed\", \"uuid\": \"feed-1\", \"name\": \"My Feed\"}";

    @Test
    void testPermissionRoutes() {
        final FixtureSession session = DocEditors.permissionRoutes(RestFixtures.builder()).build().newSession();

        final RestReply permissions = session.exchange(
                post("/permission/doc/v1/fetchDocumentUserPermissions", "{}")).getReply();
        assertThat(permissions.getBody()).contains("\"permission\": \"OWNER\"").contains("\"admin\"");
        final RestReply report = session.exchange(
                post("/permission/doc/v1/getDocUserPermissionsReport", "{}")).getReply();
        assertThat(report.getBody()).contains("OWNER");
    }

    @Test
    void testDocSelectionRoutes_decorate() {
        final FixtureSession session = DocEditors.docSelectionRoutes(RestFixtures.builder()).build().newSession();

        final RestReply reply = session.exchange(post("/explorer/v2/decorate",
                "{\"docRef\": " + FEED + ", \"requiredPermissions\": [\"USE\"]}")).getReply();

        assertThat(JsonValues.jsonEquals(reply.getBody(), FEED)).isTrue();
    }

    @Test
    void testDocSelectionRoutes_getFromDocRef() {
        final FixtureSession session = DocEditors.docSelectionRoutes(RestFixtures.builder()).build().newSession();

        final RestReply reply = session.exchange(post("/explorer/v2/getFromDocRef", FEED)).getReply();

        final Map<?, ?> node = (Map<?, ?>) JsonValues.parse(reply.getBody());
        assertThat(node.get("uuid")).isEqualTo("feed-1");
        assertThat(node.get("name")).isEqualTo("My Feed");
        assertThat(node.get("rootNodeUuid")).isEqualTo("0");
    }

    @Test
    void testDocSelectionRoutes_noDocument() {
        final FixtureSession session = DocEditors.docSelectionRoutes(RestFixtures.builder()).build().newSession();

        assertThat(session.exchange(post("/explorer/v2/decorate", "{}")).getReply().getStatus()).isEqualTo(204);
        assertThat(session.exchange(post("/explorer/v2/getFromDocRef", "null")).getReply().getStatus())
                .isEqualTo(204);
    }

    private static RecordedRequest post(final String path, final String body) {
        return new RecordedRequest("POST", path, null, body);
    }
}
