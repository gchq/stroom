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


package stroom.gwt.workbench.client.app.activity;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestActivityFixtures {

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
}
