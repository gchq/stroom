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

package stroom.mcp.impl.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestGlobs {

    @Test
    void testGlobsMatchAsFnmatchcaseDoes() {
        assertThat(Globs.matches("Event Data (JSON)", "Event Data (*)")).isTrue();
        assertThat(Globs.matches("event data (JSON)", "Event Data (*)")).isFalse();
        assertThat(Globs.matches("Indexing", "Indexing")).isTrue();
        assertThat(Globs.matches("Indexing - Elasticsearch", "Indexing")).isFalse();
        assertThat(Globs.matches("ecs-windows-v1", "ecs-windows*")).isTrue();
        assertThat(Globs.matches("a1", "a?")).isTrue();
        assertThat(Globs.matches("ab", "a[bc]")).isTrue();
        assertThat(Globs.matches("ab", "a[!bc]")).isFalse();
        assertThat(Globs.matches("a[", "a[")).isTrue();
        assertThat(Globs.matches(null, "*")).isFalse();
    }
}
