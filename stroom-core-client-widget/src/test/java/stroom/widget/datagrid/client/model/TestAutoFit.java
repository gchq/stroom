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

class TestAutoFit {

    @Test
    void testSampleRowsTakesEveryRowOfAShortPage() {
        assertThat(AutoFit.sampleRows(4, 10, Collections.emptyList())).containsExactly(0, 1, 2, 3);
        assertThat(AutoFit.sampleRows(0, 10, Collections.emptyList())).isEmpty();
    }

    @Test
    void testSampleRowsSpreadsEvenlyAndKeepsFirstAndLast() {
        assertThat(AutoFit.sampleRows(101, 5, Collections.emptyList())).containsExactly(0, 25, 50, 75, 100);
    }

    @Test
    void testSampleRowsAddsRowsInView() {
        // Rows off the page are ignored.
        assertThat(AutoFit.sampleRows(101, 3, Arrays.asList(51, 52, 200, -1)))
                .containsExactly(0, 50, 51, 52, 100);
    }

    @Test
    void testSampleRowsWithTinyMaximum() {
        assertThat(AutoFit.sampleRows(10, 1, Collections.emptyList())).containsExactly(0);
        assertThat(AutoFit.sampleRows(10, 0, Collections.singletonList(3))).isEmpty();
    }

    @Test
    void testWidthAddsAPixelAndHonoursMinimum() {
        assertThat(AutoFit.width(99.4, 30)).isEqualTo(100);
        assertThat(AutoFit.width(99.0, 30)).isEqualTo(100);
        assertThat(AutoFit.width(5, 30)).isEqualTo(30);
    }
}
