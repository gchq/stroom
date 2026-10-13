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

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapFactStatus {

    @Test
    void anAbsentOrBlankStatusIsActive() {
        // Every fact written without the field must keep drawing.
        assertThat(FloorMapFactStatus.fromValue(null)).isEqualTo(FloorMapFactStatus.ACTIVE);
        assertThat(FloorMapFactStatus.fromValue("")).isEqualTo(FloorMapFactStatus.ACTIVE);
        assertThat(FloorMapFactStatus.fromValue("   ")).isEqualTo(FloorMapFactStatus.ACTIVE);
    }

    @Test
    void matchingIgnoresCaseAndSurroundingWhitespace() {
        assertThat(FloorMapFactStatus.fromValue("DELETED")).isEqualTo(FloorMapFactStatus.DELETED);
        assertThat(FloorMapFactStatus.fromValue("deleted")).isEqualTo(FloorMapFactStatus.DELETED);
        assertThat(FloorMapFactStatus.fromValue(" Deleted\t")).isEqualTo(FloorMapFactStatus.DELETED);
        assertThat(FloorMapFactStatus.fromValue("active")).isEqualTo(FloorMapFactStatus.ACTIVE);
    }

    @Test
    void anUnrecognisedStatusIsNullSoTheCallerCanWarnAndKeepTheFact() {
        assertThat(FloorMapFactStatus.fromValue("open")).isNull();
        assertThat(FloorMapFactStatus.fromValue("DELETE")).isNull();
    }

    @Test
    void onlyDeletedHides() {
        assertThat(FloorMapFactStatus.DELETED.isHidden()).isTrue();
        assertThat(FloorMapFactStatus.ACTIVE.isHidden()).isFalse();
    }
}
