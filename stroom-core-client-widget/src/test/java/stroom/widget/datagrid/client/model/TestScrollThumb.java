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
import static org.assertj.core.api.Assertions.within;

class TestScrollThumb {

    @Test
    void testHiddenWhenContentFits() {
        final ScrollThumb thumb = ScrollThumb.compute(0, 500, 400, 500, 20);

        assertThat(thumb.isVisible()).isFalse();
        assertThat(thumb.scrollForPosition(100)).isZero();
    }

    @Test
    void testLengthIsProportional() {
        final ScrollThumb thumb = ScrollThumb.compute(0, 500, 1000, 500, 20);

        assertThat(thumb.isVisible()).isTrue();
        assertThat(thumb.getLength()).isEqualTo(250);
        assertThat(thumb.getTravel()).isEqualTo(250);
        assertThat(thumb.getPosition()).isZero();
    }

    @Test
    void testPositionAtEnd() {
        final ScrollThumb thumb = ScrollThumb.compute(500, 500, 1000, 500, 20);

        assertThat(thumb.getPosition()).isEqualTo(250);
    }

    @Test
    void testMinimumLengthForHugeContent() {
        final ScrollThumb thumb = ScrollThumb.compute(0, 500, 2_400_000, 500, 20);

        assertThat(thumb.getLength()).isEqualTo(20);
        assertThat(thumb.getTravel()).isEqualTo(480);
    }

    @Test
    void testScrollForPositionIsInverseOfPosition() {
        final ScrollThumb thumb = ScrollThumb.compute(123, 500, 5000, 480, 20);

        assertThat(thumb.scrollForPosition(thumb.getPosition())).isCloseTo(123, within(0.001));
        // Dragging past either end clamps to the scroll range.
        assertThat(thumb.scrollForPosition(-50)).isZero();
        assertThat(thumb.scrollForPosition(10_000)).isEqualTo(4500);
    }
}
