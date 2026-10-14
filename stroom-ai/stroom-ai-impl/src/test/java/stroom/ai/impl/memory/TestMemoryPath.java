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

package stroom.ai.impl.memory;

import stroom.ai.shared.AiMemoryScope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestMemoryPath {

    @Test
    void testParse() {
        assertThat(MemoryPath.parse("/memories/prefs.md"))
                .contains(new MemoryPath(AiMemoryScope.USER, "prefs.md"));
        assertThat(MemoryPath.parse(" /memories/session/plan.md "))
                .contains(new MemoryPath(AiMemoryScope.CHAT, "plan.md"));
        assertThat(MemoryPath.parse("/memories/session/plan.md").orElseThrow().path())
                .isEqualTo("/memories/session/plan.md");
        assertThat(MemoryPath.parse("/memories/session/plan.md").orElseThrow().scopeKey(12)).isEqualTo("12");
        assertThat(MemoryPath.parse("/memories/a").orElseThrow().scopeKey(12)).isEmpty();
    }

    @Test
    void testParse_rejectsAnythingElse() {
        assertThat(MemoryPath.parse(null)).isEmpty();
        assertThat(MemoryPath.parse("/memories/")).isEmpty();
        assertThat(MemoryPath.parse("/memories/session/")).isEmpty();
        assertThat(MemoryPath.parse("/memories/../etc/passwd")).isEmpty();
        assertThat(MemoryPath.parse("/memories/..")).isEmpty();
        assertThat(MemoryPath.parse("/memories/.hidden")).isEmpty();
        assertThat(MemoryPath.parse("/memories/a/b")).isEmpty();
        assertThat(MemoryPath.parse("/memories/a b")).isEmpty();
        assertThat(MemoryPath.parse("/outputs/a")).isEmpty();
        assertThat(MemoryPath.parse("memories/a")).isEmpty();
        assertThat(MemoryPath.parse("/memories/" + "a".repeat(65))).isEmpty();
        assertThat(MemoryPath.parse("/memories/" + "a".repeat(64))).isPresent();
    }
}
