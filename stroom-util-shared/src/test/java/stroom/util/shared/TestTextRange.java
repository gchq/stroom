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

package stroom.util.shared;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestTextRange {

    @Test
    void testIsInsideRange() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        // Exactly the same as the range
        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        Assertions.assertThat(result)
                .isTrue();
    }

    @Test
    void testIsInsideRange2() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 29));

        Assertions.assertThat(result)
                .isFalse();
    }

    @Test
    void testIsInsideRange3() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 11),
                DefaultLocation.of(1, 30));

        Assertions.assertThat(result)
                .isFalse();
    }

    @Test
    void testIsInsideRange4() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 15),
                DefaultLocation.of(1, 25));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 1),
                DefaultLocation.of(1, 2000));

        Assertions.assertThat(result)
                .isTrue();
    }

    @Test
    void testIsInsideRange5() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 12),
                DefaultLocation.of(1, 28));

        Assertions.assertThat(result)
                .isFalse();
    }
}
