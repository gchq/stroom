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

package stroom.gwt.workbench.framework.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestJson {

    @Test
    void testQuote() {
        assertThat(Json.quote(null)).isEqualTo("null");
        assertThat(Json.quote("")).isEqualTo("\"\"");
        assertThat(Json.quote("a \"b\" \\ c")).isEqualTo("\"a \\\"b\\\" \\\\ c\"");
        assertThat(Json.quote("1\n2\r3\t4")).isEqualTo("\"1\\n2\\r3\\t4\"");
        // Control characters and HTML brackets are escaped
        assertThat(Json.quote("\u0001<script>")).isEqualTo("\"\\u0001\\u003cscript\\u003e\"");
        assertThat(Json.quote("colour ✓")).isEqualTo("\"colour ✓\"");
    }

    @Test
    void testArray() {
        assertThat(Json.array(List.of())).isEqualTo("[]");
        assertThat(Json.array(List.of("a", "b\"c"))).isEqualTo("[\"a\",\"b\\\"c\"]");
    }
}
