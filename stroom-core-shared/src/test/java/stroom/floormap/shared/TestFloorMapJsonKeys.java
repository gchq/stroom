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

package stroom.floormap.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The DOM id namespace for map objects on the canvas.
class TestFloorMapJsonKeys {

    @Test
    void testAnObjectDomIdRoundTrips() {
        for (final String key : List.of("desk-1", "Room 1", "café", "__weird", "x")) {
            assertThat(FloorMapJsonKeys.objectKeyFromDomId(FloorMapJsonKeys.objectDomId(key)))
                    .as("key %s", key)
                    .isEqualTo(key);
        }
    }

    /// Regression test: object keys were used as DOM ids as they stood, so an entity whose id
    /// happened to be `__handle_rotate` was taken for the rotate handle, and `__cluster_x` for a
    /// cluster. Namespaced, neither can be mistaken for anything but an object.
    @Test
    void testAnObjectKeyThatLooksLikeAReservedIdIsStillAnObject() {
        for (final String key : List.of(
                FloorMapJsonKeys.HANDLE_PREFIX + "rotate",
                FloorMapJsonKeys.CLUSTER_PREFIX + "desk-1",
                FloorMapJsonKeys.SVG_GROUP_PREFIX + "desk-1")) {
            final String domId = FloorMapJsonKeys.objectDomId(key);

            assertThat(domId)
                    .as("dom id for key %s", key)
                    .doesNotStartWith(FloorMapJsonKeys.HANDLE_PREFIX)
                    .doesNotStartWith(FloorMapJsonKeys.CLUSTER_PREFIX)
                    .doesNotStartWith(FloorMapJsonKeys.SVG_GROUP_PREFIX);
            assertThat(FloorMapJsonKeys.objectKeyFromDomId(domId)).isEqualTo(key);
        }
    }

    @Test
    void testIdsThatAreNotObjectsGiveNoKey() {
        assertThat(FloorMapJsonKeys.objectKeyFromDomId(null)).isNull();
        assertThat(FloorMapJsonKeys.objectKeyFromDomId("")).isNull();
        assertThat(FloorMapJsonKeys.objectKeyFromDomId(FloorMapJsonKeys.OBJECT_PREFIX)).isNull();
        assertThat(FloorMapJsonKeys.objectKeyFromDomId("desk-1")).isNull();
        assertThat(FloorMapJsonKeys.objectKeyFromDomId(FloorMapJsonKeys.HANDLE_PREFIX + "rotate")).isNull();
        assertThat(FloorMapJsonKeys.objectKeyFromDomId(FloorMapJsonKeys.CLUSTER_PREFIX + "desk-1")).isNull();
        // An object's wrapper group is not the object either.
        assertThat(FloorMapJsonKeys.objectKeyFromDomId(
                FloorMapJsonKeys.SVG_GROUP_PREFIX + FloorMapJsonKeys.objectDomId("desk-1")))
                .isNull();
    }
}
