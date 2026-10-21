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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVirtualWindow {

    @Test
    void testTopOfList() {
        final VirtualWindow window = VirtualWindow.compute(0, 240, 24, 10_000, 5);

        // 10 visible + 1 partial, + 5 buffer below; nothing above row 0.
        assertThat(window.getStart()).isZero();
        assertThat(window.getEnd()).isEqualTo(16);
        assertThat(window.getOffset()).isZero();
        assertThat(window.getContentHeight()).isEqualTo(240_000);
    }

    @Test
    void testMiddleOfList() {
        final VirtualWindow window = VirtualWindow.compute(2400, 240, 24, 10_000, 5);

        assertThat(window.getStart()).isEqualTo(95);
        assertThat(window.getEnd()).isEqualTo(116);
        assertThat(window.getOffset()).isEqualTo(95 * 24);
        assertThat(window.contains(100)).isTrue();
        assertThat(window.contains(116)).isFalse();
    }

    @Test
    void testScrollPastEndIsClamped() {
        final VirtualWindow window = VirtualWindow.compute(1_000_000, 240, 24, 100, 5);

        assertThat(window.getEnd()).isEqualTo(100);
        assertThat(window.getStart()).isEqualTo(85);
    }

    @Test
    void testFewerRowsThanViewport() {
        final VirtualWindow window = VirtualWindow.compute(0, 1000, 24, 3, 5);

        assertThat(window.getStart()).isZero();
        assertThat(window.getEnd()).isEqualTo(3);
        assertThat(window.size()).isEqualTo(3);
    }

    @Test
    void testFractionalRowHeight() {
        // A natural one-line row at the default density is 23.0625px.
        final VirtualWindow window = VirtualWindow.compute(2306.25, 230, 23.0625, 1000, 5);

        assertThat(window.getStart()).isEqualTo(95);
        assertThat(window.getOffset()).isEqualTo(95 * 23.0625);
        assertThat(window.getContentHeight()).isEqualTo(23062.5);
    }

    @Test
    void testNoRows() {
        final VirtualWindow window = VirtualWindow.compute(0, 240, 24, 0, 5);

        assertThat(window.size()).isZero();
        assertThat(window.getContentHeight()).isZero();
    }

    @Test
    void testZeroRowHeightIsRejected() {
        assertThatThrownBy(() -> VirtualWindow.compute(0, 240, 0, 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
