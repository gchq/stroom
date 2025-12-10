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

package stroom.util.collections;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestRingBuffer {

    @Test
    void testAdd() {
        final RingBuffer<Integer> ringBuffer = new RingBuffer<>(3);

        ringBuffer.add(1);
        assertThat(ringBuffer)
                .containsExactly(1);
        ringBuffer.add(2);
        assertThat(ringBuffer)
                .containsExactly(1, 2);
        ringBuffer.add(3);
        assertThat(ringBuffer)
                .containsExactly(1, 2, 3);
        ringBuffer.add(4);
        assertThat(ringBuffer)
                .containsExactly(2, 3, 4);
        ringBuffer.add(5);
        assertThat(ringBuffer)
                .containsExactly(3, 4, 5);
    }

    @Test
    void testDescendingIterator() {
        final RingBuffer<Integer> ringBuffer = new RingBuffer<>(5);

        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.add(3);
        ringBuffer.add(4);
        ringBuffer.add(5);
        ringBuffer.add(6);

        final List<Integer> reverse = new ArrayList<>(5);
        ringBuffer.descendingIterator().forEachRemaining(reverse::add);
        assertThat(reverse)
                .containsExactly(6, 5, 4, 3, 2);
    }

    @Test
    void testContainsTailElements() {
        final RingBuffer<Integer> ringBuffer = new RingBuffer<>(5);
        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.add(3);
        ringBuffer.add(4);
        ringBuffer.add(5);
        ringBuffer.add(6);

        assertThat(ringBuffer.containsTailElements((Integer[]) null))
                .isFalse();
        final Integer[] emptyArr = new Integer[0];
        assertThat(ringBuffer.containsTailElements(emptyArr))
                .isFalse();
        assertThat(ringBuffer.containsTailElements(1, 2, 3, 4, 5, 6))
                .isFalse();
        assertThat(ringBuffer.containsTailElements(2, 3, 4))
                .isFalse();
        assertThat(ringBuffer.containsTailElements(6))
                .isTrue();
        assertThat(ringBuffer.containsTailElements(5, 6))
                .isTrue();
        assertThat(ringBuffer.containsTailElements(4, 5, 6))
                .isTrue();
        assertThat(ringBuffer.containsTailElements(2, 3, 4, 5, 6))
                .isTrue();
    }
}
