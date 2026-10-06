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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoryLocationManager {

    private static final String BASE = "http://localhost:6008/";

    @Test
    void testRelativeToHostPage() {
        assertThat(StoryLocationManager.relativeTo(BASE + "resourcestore/my-notes.md?uuid=k1", BASE))
                .isEqualTo("resourcestore/my-notes.md?uuid=k1");
    }

    @Test
    void testOtherUrlsAreUnchanged() {
        assertThat(StoryLocationManager.relativeTo("https://example.com/file", BASE))
                .isEqualTo("https://example.com/file");
        assertThat(StoryLocationManager.relativeTo("resourcestore/x", BASE))
                .isEqualTo("resourcestore/x");
    }

    @Test
    void testNulls() {
        assertThat(StoryLocationManager.relativeTo(null, BASE)).isNull();
        assertThat(StoryLocationManager.relativeTo(BASE + "a", null)).isEqualTo(BASE + "a");
    }
}
