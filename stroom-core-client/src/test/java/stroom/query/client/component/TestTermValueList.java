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

package stroom.query.client.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTermValueList {

    @Test
    void testParseTrimsAndDropsBlankValues() {
        assertThat(TermValueList.parse("error, warn ,  info")).containsExactly("error", "warn", "info");
        assertThat(TermValueList.parse("error,,warn,")).containsExactly("error", "warn");
        assertThat(TermValueList.parse("  ")).isEmpty();
        assertThat(TermValueList.parse(null)).isEmpty();
    }

    @Test
    void testToggleAddsAValueNotInTheList() {
        assertThat(TermValueList.toggle("error", "warn")).isEqualTo("error, warn");
        assertThat(TermValueList.toggle("", "warn")).isEqualTo("warn");
        assertThat(TermValueList.toggle(null, "warn")).isEqualTo("warn");
    }

    @Test
    void testToggleRemovesAValueInTheList() {
        assertThat(TermValueList.toggle("error, warn", "error")).isEqualTo("warn");
        assertThat(TermValueList.toggle("warn", "warn")).isEmpty();
        // The list is written again in one form, whatever spacing it was typed with
        assertThat(TermValueList.toggle("error,warn,info", "warn")).isEqualTo("error, info");
    }

    @Test
    void testQuotedValuesAreKeptAsTheyAre() {
        // The data source quotes a value as its query syntax needs, e.g. one holding a space
        assertThat(TermValueList.toggle("error", "\"not found\"")).isEqualTo("error, \"not found\"");
        assertThat(TermValueList.toggle("error, \"not found\"", "\"not found\"")).isEqualTo("error");
    }
}
