/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.language.functions;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParserSelections extends AbstractExpressionParserTest {

    @Test
    void testAny() {
        // Check that any just returns the cell value ignoring children.
        testSelectors("any(${val1})",
                IntStream.rangeClosed(1, 10),
                val -> assertThat(val.toDouble())
                        .isEqualTo(300, Offset.offset(0D)));
    }


    @Test
    void testFirst() {
        testSelectors("first(${val1})",
                IntStream.rangeClosed(1, 10),
                val -> assertThat(val.toDouble())
                        .isEqualTo(1, Offset.offset(0D)));
    }

    @Test
    void testLast() {
        testSelectors("last(${val1})",
                IntStream.rangeClosed(1, 10),
                val -> assertThat(val.toDouble())
                        .isEqualTo(10, Offset.offset(0D)));
    }

    @Test
    void testNth() {
        testSelectors("nth(${val1}, 7)",
                IntStream.rangeClosed(1, 10),
                val -> assertThat(val.toDouble())
                        .isEqualTo(7, Offset.offset(0D)));
    }

    @Test
    void testTop() {
        testSelectors("top(${val1}, ',', 3)",
                IntStream.rangeClosed(1, 10),
                val -> assertThat(val.toString())
                        .isEqualTo("1,2,3"));
    }

    @Test
    void testTopSmall() {
        testSelectors("top(${val1}, ',', 3)",
                IntStream.rangeClosed(1, 2),
                val -> assertThat(val.toString())
                        .isEqualTo("1,2"));
    }

    @Test
    void testBottom() {
        testSelectors("bottom(${val1}, ',', 3)",
                IntStream.rangeClosed(1, 10),
                val -> assertThat(val.toString())
                        .isEqualTo("8,9,10"));
    }

    @Test
    void testBottomSmall() {
        testSelectors("bottom(${val1}, ',', 3)",
                IntStream.rangeClosed(1, 2),
                val -> assertThat(val.toString())
                        .isEqualTo("1,2"));
    }
}
