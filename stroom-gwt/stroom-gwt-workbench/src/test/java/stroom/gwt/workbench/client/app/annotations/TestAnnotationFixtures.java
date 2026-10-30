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


package stroom.gwt.workbench.client.app.annotations;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnnotationFixtures {

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
}
