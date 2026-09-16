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

package stroom.proxy.app.pipeline.stage.forward;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestForwardBounds {

    @Test
    void testTheDelayGrowsByTheFactorToTheCap() {
        final ForwardBounds bounds = new ForwardBounds(
                Duration.ofDays(7), Duration.ofMinutes(1), 2, Duration.ofMinutes(5));
        assertThat(bounds.delayAfter(0)).isEqualTo(Duration.ZERO);
        assertThat(bounds.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(bounds.delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(bounds.delayAfter(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(bounds.delayAfter(4)).isEqualTo(Duration.ofMinutes(5));
        assertThat(bounds.delayAfter(50)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void testAGrowthFactorOfOneIsFlatWhateverTheCap() {
        final ForwardBounds bounds = new ForwardBounds(
                Duration.ofDays(7), Duration.ofMinutes(10), 1, Duration.ofMinutes(1));
        assertThat(bounds.delayAfter(1)).isEqualTo(Duration.ofMinutes(10));
        assertThat(bounds.delayAfter(9)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void testBoundsMustBeSane() {
        assertThatThrownBy(() -> new ForwardBounds(Duration.ofDays(-1), Duration.ZERO, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ForwardBounds(Duration.ofDays(1), Duration.ZERO, 0.5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
