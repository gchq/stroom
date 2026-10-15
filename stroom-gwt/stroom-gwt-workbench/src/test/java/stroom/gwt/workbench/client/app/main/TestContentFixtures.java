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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentFixtures {

    @Test
    void testActivityUiConfig_enablesActivitiesWithTheEditorTemplate() {
        final Map<?, ?> uiConfig = (Map<?, ?>) JsonValues.parse(ActivityFixtures.uiConfig("Pick One"));
        final Map<?, ?> activity = (Map<?, ?>) uiConfig.get("activity");

        assertThat(activity.get("enabled")).isEqualTo(true);
        assertThat(activity.get("managerTitle")).isEqualTo("Pick One");
        assertThat(activity.get("editorBody")).isEqualTo(ActivityFixtures.EDITOR_BODY);
        // The rest of the default UI config is kept
        assertThat(uiConfig.get("htmlTitle")).isEqualTo("Stroom");
    }

    @Test
    void testActivityPage_holdsTheActivitiesAndTheirProperties() {
        final String json = ActivityFixtures.page(
                ActivityFixtures.activity(1, ActivityFixtures.prop("code", "Code", "CASE-1", true)),
                ActivityFixtures.activity(2));
        final Map<?, ?> page = (Map<?, ?>) JsonValues.parse(json);
        final List<?> values = (List<?>) page.get("values");

        assertThat(values).hasSize(2);
        assertThat(JsonValues.jsonContains(json, "{\"values\": [{\"details\": {\"properties\": [{\"id\": "
                + "\"code\", \"value\": \"CASE-1\", \"showInList\": true, \"showInSelection\": true}]}}, "
                + "{\"details\": {\"properties\": []}}]}")).isTrue();
        assertThat(JsonValues.jsonContains(json, "{\"pageResponse\": {\"total\": 2}}")).isTrue();
    }

    @Test
    void testAnnotationEntry_withAndWithoutAPreviousValue() {
        final String withPrevious = AnnotationFixtures.entry(1, "STATUS", "Alice", 10L, "Closed", "Open");
        final String withoutPrevious = AnnotationFixtures.entry(2, "TITLE", "Alice", 20L, "First", null);

        assertThat(JsonValues.jsonContains(withPrevious, "{\"entryType\": \"STATUS\", \"entryUser\": "
                + "{\"subjectId\": \"alice\", \"displayName\": \"Alice\"}, \"entryValue\": {\"type\": \"string\", "
                + "\"value\": \"Closed\"}, \"previousValue\": {\"type\": \"string\", \"value\": \"Open\"}}")).isTrue();
        assertThat(((Map<?, ?>) JsonValues.parse(withoutPrevious)).containsKey("previousValue")).isFalse();
    }

    @Test
    void testAnnotationPage_emptyAndFull() {
        assertThat(JsonValues.jsonEquals(AnnotationFixtures.page(),
                "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, \"exact\": true}}"))
                .isTrue();
        assertThat(JsonValues.jsonContains(AnnotationFixtures.page("{\"id\": 1}", "{\"id\": 2}"),
                "{\"pageResponse\": {\"length\": 2, \"total\": 2}}")).isTrue();
    }

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
