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

class TestKeyboardRow {

    @Test
    void testClamp() {
        assertThat(KeyboardRow.clamp(5, 10)).isEqualTo(5);
        assertThat(KeyboardRow.clamp(15, 10)).isEqualTo(9);
        assertThat(KeyboardRow.clamp(-1, 10)).isEqualTo(0);
        // An empty page still has keyboard row 0, as GWT reports it.
        assertThat(KeyboardRow.clamp(5, 0)).isEqualTo(0);
    }

    @Test
    void testRowFollowsItsKey() {
        // The same rows: it stays put.
        assertThat(KeyboardRow.bestMatch("f", 5, Arrays.asList("a", "b", "c", "d", "e", "f", "g")))
                .isEqualTo(5);
        // A row was inserted above it.
        assertThat(KeyboardRow.bestMatch("f", 5, Arrays.asList("x", "a", "b", "c", "d", "e", "f")))
                .isEqualTo(6);
    }

    @Test
    void testGoneRowHasNoMatch() {
        // Measured: MyDataGrid's keyboard row goes back to 0 when its row's values change.
        assertThat(KeyboardRow.bestMatch("f", 5, Arrays.asList("a2", "b2", "c2", "d2", "e2", "f2")))
                .isEqualTo(-1);
        assertThat(KeyboardRow.bestMatch("f", 5, Collections.emptyList())).isEqualTo(-1);
    }

    @Test
    void testBestMatchIsNearestAndEarlierOnATie() {
        assertThat(KeyboardRow.bestMatch("a", 3, Arrays.asList("a", "x", "x", "x", "x", "a", "x")))
                .isEqualTo(5);
        assertThat(KeyboardRow.bestMatch("a", 2, Arrays.asList("a", "x", "x", "x", "a"))).isEqualTo(0);
        assertThat(KeyboardRow.bestMatch("a", 0, Arrays.asList("x", null))).isEqualTo(-1);
        assertThat(KeyboardRow.bestMatch(null, 0, Arrays.asList("x", null))).isEqualTo(-1);
    }
}
