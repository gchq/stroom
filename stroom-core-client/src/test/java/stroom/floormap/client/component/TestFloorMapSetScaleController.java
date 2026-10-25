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

package stroom.floormap.client.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapSetScaleController {

    @Test
    void aPositiveNumberIsADistance() {
        assertThat(FloorMapSetScaleController.parseDistance("2.4")).isEqualTo(2.4);
        assertThat(FloorMapSetScaleController.parseDistance(" 12 ")).isEqualTo(12.0);
    }

    @Test
    void nothingIsNoDistance() {
        assertThat(FloorMapSetScaleController.parseDistance(null)).isNull();
        assertThat(FloorMapSetScaleController.parseDistance("")).isNull();
        assertThat(FloorMapSetScaleController.parseDistance("   ")).isNull();
    }

    @Test
    void anythingElseIsNoDistance() {
        assertThat(FloorMapSetScaleController.parseDistance("0")).isNull();
        assertThat(FloorMapSetScaleController.parseDistance("-1")).isNull();
        assertThat(FloorMapSetScaleController.parseDistance("two metres")).isNull();
        assertThat(FloorMapSetScaleController.parseDistance("NaN")).isNull();
        assertThat(FloorMapSetScaleController.parseDistance("Infinity")).isNull();
    }
}
