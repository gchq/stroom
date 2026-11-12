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

package stroom.floormap.client.cluster;

import stroom.floormap.client.geometry.FloorMapScreenGeometry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapCluster {

    private static final double TOL = 1e-9;

    @Test
    void aLoneEntityIsThePointGlyphSize() {
        assertThat(sizePx(1)).isCloseTo(FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX, within(TOL));
        assertThat(sizePx(0)).isCloseTo(FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX, within(TOL));
    }

    @Test
    void aPairIsTheClusterGlyphSize() {
        // A merged set is a step bigger than one entity: 32 px to its 24
        assertThat(FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX).isEqualTo(24);
        assertThat(sizePx(2)).isCloseTo(FloorMapScreenGeometry.CLUSTER_GLYPH_SIZE_PX, within(TOL));
        assertThat(sizePx(2)).isCloseTo(32.0, within(TOL));
    }

    @Test
    void biggerClustersGrowFromThePair() {
        assertThat(sizePx(20)).isGreaterThan(sizePx(10));
        assertThat(sizePx(10)).isGreaterThan(sizePx(2));
        // Ten times a pair is 35% bigger than a pair
        assertThat(sizePx(20)).isCloseTo(32 * 1.35, within(TOL));
    }

    @Test
    void growthIsCappedAtTwiceALoneEntity() {
        assertThat(sizePx(1_000_000)).isCloseTo(48.0, within(TOL));
        assertThat(FloorMapCluster.sizeFactor(1_000_000)).isCloseTo(2.0, within(TOL));
    }

    private static double sizePx(final int memberCount) {
        return FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX * FloorMapCluster.sizeFactor(memberCount);
    }
}
