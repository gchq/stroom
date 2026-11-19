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

import static org.assertj.core.api.Assertions.assertThat;

class TestTreePositions {

    @Test
    void testFlatRowsAreAllSiblings() {
        final TreePositions positions = TreePositions.of(new int[]{0, 0, 0});

        assertThat(positions.getPosition(0)).isEqualTo(1);
        assertThat(positions.getPosition(2)).isEqualTo(3);
        assertThat(positions.getSize(1)).isEqualTo(3);
    }

    @Test
    void testChildrenCountAmongThemselves() {
        // A (B, C (D)), E
        final TreePositions positions = TreePositions.of(new int[]{0, 1, 1, 2, 0});

        // A and E are the top level's two
        assertThat(positions.getPosition(0)).isEqualTo(1);
        assertThat(positions.getSize(0)).isEqualTo(2);
        assertThat(positions.getPosition(4)).isEqualTo(2);
        assertThat(positions.getSize(4)).isEqualTo(2);
        // B and C are A's two
        assertThat(positions.getPosition(1)).isEqualTo(1);
        assertThat(positions.getPosition(2)).isEqualTo(2);
        assertThat(positions.getSize(2)).isEqualTo(2);
        // D is C's only child
        assertThat(positions.getPosition(3)).isEqualTo(1);
        assertThat(positions.getSize(3)).isEqualTo(1);
    }

    @Test
    void testSameDepthUnderAnotherParentStartsAgain() {
        // A (B), C (D, E)
        final TreePositions positions = TreePositions.of(new int[]{0, 1, 0, 1, 1});

        assertThat(positions.getSize(1)).isEqualTo(1);
        assertThat(positions.getPosition(3)).isEqualTo(1);
        assertThat(positions.getPosition(4)).isEqualTo(2);
        assertThat(positions.getSize(4)).isEqualTo(2);
    }

    @Test
    void testOutOfRangeIsZero() {
        final TreePositions positions = TreePositions.of(new int[]{0});

        assertThat(positions.getPosition(-1)).isZero();
        assertThat(positions.getSize(5)).isZero();
    }

    @Test
    void testNoRows() {
        assertThat(TreePositions.of(new int[0]).getSize(0)).isZero();
    }
}
