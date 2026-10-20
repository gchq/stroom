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

package stroom.gwt.workbench.framework.client.preview;

import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryRenderer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestRunnerJson {

    private static final StoryRenderer NO_WIDGET = context -> null;

    @Test
    void testIndex() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button", TestRunnerJson.class)
                .tags("autodocs")
                .story("Default", NO_WIDGET)
                .story("DialogClose", "Dialog — \"Close\" button", NO_WIDGET)
                .withPlay(play -> {
                });

        assertThat(RunnerJson.index(registry)).isEqualTo(
                "{\"v\":5,\"entries\":{"
                + "\"widgets-buttons-button--default\":{\"type\":\"story\","
                + "\"id\":\"widgets-buttons-button--default\",\"title\":\"Widgets/Buttons/Button\","
                + "\"name\":\"Default\",\"tags\":[\"autodocs\"],\"hasPlay\":false,"
                + "\"sourceClass\":\"" + TestRunnerJson.class.getName() + "\"},"
                + "\"widgets-buttons-button--dialog-close\":{\"type\":\"story\","
                + "\"id\":\"widgets-buttons-button--dialog-close\",\"title\":\"Widgets/Buttons/Button\","
                + "\"name\":\"Dialog — \\\"Close\\\" button\",\"tags\":[\"autodocs\",\"play-fn\"],"
                + "\"hasPlay\":true,"
                + "\"sourceClass\":\"" + TestRunnerJson.class.getName() + "\"}}}");
    }

    @Test
    void testIndex_empty() {
        assertThat(RunnerJson.index(new StoryRegistry())).isEqualTo("{\"v\":5,\"entries\":{}}");
    }

    @Test
    void testIndex_noSourceClass() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("A").story("B", NO_WIDGET);

        assertThat(RunnerJson.index(registry)).doesNotContain("sourceClass");
    }

    @Test
    void testPlayState() {
        assertThat(RunnerJson.playState("a--b", "COMPLETED", false, List.of(), 0, 0, null))
                .isEqualTo("{\"storyId\":\"a--b\",\"status\":\"COMPLETED\",\"hasPlay\":false,"
                           + "\"nextStep\":0,\"stepCount\":0,\"failedStep\":null,\"error\":null,"
                           + "\"entries\":[]}");
    }

    @Test
    void testPlayState_renderError() {
        // e.g. the story wasn't found, so there is no id
        assertThat(RunnerJson.playState(null, "ERRORED", true, List.of(), 0, 0, "Couldn't find\n'x'"))
                .isEqualTo("{\"storyId\":null,\"status\":\"ERRORED\",\"hasPlay\":true,"
                           + "\"nextStep\":0,\"stepCount\":0,\"failedStep\":null,"
                           + "\"error\":\"Couldn't find\\n'x'\",\"entries\":[]}");
    }

    @Test
    void testQuote() {
        assertThat(RunnerJson.quote(null)).isEqualTo("null");
        assertThat(RunnerJson.quote("")).isEqualTo("\"\"");
        assertThat(RunnerJson.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(RunnerJson.quote("a\nb\rc\td")).isEqualTo("\"a\\nb\\rc\\td\"");
        assertThat(RunnerJson.quote("\u0001\u001f")).isEqualTo("\"\\u0001\\u001f\"");
        assertThat(RunnerJson.quote("a b")).isEqualTo("\"a\\u2028b\"");
        // Non-ASCII is fine in JSON
        assertThat(RunnerJson.quote("Dialog — Close ✓")).isEqualTo("\"Dialog — Close ✓\"");
    }
}
