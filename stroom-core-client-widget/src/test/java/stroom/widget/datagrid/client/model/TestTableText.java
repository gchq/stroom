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

package stroom.widget.datagrid.client.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableText {

    @Test
    void testNormaliseWhitespace() {
        assertThat(TableText.normaliseWhitespace("  a \n\t b  ")).isEqualTo("a b");
        assertThat(TableText.normaliseWhitespace("")).isEmpty();
        assertThat(TableText.normaliseWhitespace(null)).isEmpty();
    }

    @Test
    void testEscapeCsv() {
        assertThat(TableText.escapeCsv("plain")).isEqualTo("plain");
        assertThat(TableText.escapeCsv("a,b")).isEqualTo("\"a,b\"");
        assertThat(TableText.escapeCsv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(TableText.escapeCsv("two\nlines")).isEqualTo("two lines");
        assertThat(TableText.escapeCsv(null)).isEmpty();
    }

    @Test
    void testColumnOffsetSkipsLeadingBlankHeaders() {
        assertThat(TableText.columnOffset(Arrays.asList("", " ", "Name", "", "Value"))).isEqualTo(2);
        assertThat(TableText.columnOffset(Arrays.asList("Name", "Value"))).isZero();
        assertThat(TableText.columnOffset(Arrays.asList("", ""))).isEqualTo(2);
        assertThat(TableText.columnOffset(Collections.emptyList())).isZero();
    }

    @Test
    void testToCsvLine() {
        assertThat(TableText.toCsvLine(Arrays.asList("a", "b,c", ""))).isEqualTo("a,\"b,c\",");
        assertThat(TableText.toCsvLine(Collections.emptyList())).isEmpty();
    }

    @Test
    void testJoinLines() {
        assertThat(TableText.joinLines(Arrays.asList("a", "", "b"))).isEqualTo("a\n\nb");
    }
}
