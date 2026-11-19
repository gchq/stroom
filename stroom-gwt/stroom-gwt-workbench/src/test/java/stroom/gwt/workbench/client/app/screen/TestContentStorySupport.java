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

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestReply;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentStorySupport {

    @Test
    void testDecorate_repliesWithTheDocumentAskedAbout() {
        final RestReply reply = ContentStorySupport.decorate(new RecordedRequest("POST", "/explorer/v2/decorate",
                null, "{\"docRef\": {\"type\": \"Pipeline\", \"uuid\": \"p1\", \"name\": \"My \\\"pipe\\\"\"}, "
                + "\"requiredPermissions\": [\"VIEW\"]}"));

        assertThat(reply.getStatus()).isEqualTo(200);
        assertThat(JsonValues.jsonEquals(reply.getBody(),
                "{\"type\": \"Pipeline\", \"uuid\": \"p1\", \"name\": \"My \\\"pipe\\\"\"}")).isTrue();
    }

    @Test
    void testDecorate_withNoName() {
        final RestReply reply = ContentStorySupport.decorate(new RecordedRequest("POST", "/explorer/v2/decorate",
                null, "{\"docRef\": {\"type\": \"Folder\", \"uuid\": \"0\"}}"));

        assertThat(JsonValues.jsonEquals(reply.getBody(), "{\"type\": \"Folder\", \"uuid\": \"0\", \"name\": null}"))
                .isTrue();
    }
}
