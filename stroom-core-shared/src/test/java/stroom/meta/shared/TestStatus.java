/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.meta.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatus {

    @Test
    void testStatusValues() {
        assertThat(Status.UNLOCKED.getPrimitiveValue())
                .isEqualTo((byte) 0);
        assertThat(Status.LOCKED.getPrimitiveValue())
                .isEqualTo((byte) 1);
        assertThat(Status.DELETED.getPrimitiveValue())
                .isEqualTo((byte) 99);
    }

    @Test
    void testStatusDisplayValues() {
        assertThat(Status.UNLOCKED.getDisplayValue())
                .isEqualTo("Unlocked");
        assertThat(Status.LOCKED.getDisplayValue())
                .isEqualTo("Locked");
        assertThat(Status.DELETED.getDisplayValue())
                .isEqualTo("Deleted");
    }

    @Test
    void testPrimitiveValueConverter() {
        assertThat(Status.UNLOCKED)
                .isEqualTo(Status.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue((byte) 0));
        assertThat(Status.LOCKED)
                .isEqualTo(Status.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue((byte) 1));
        assertThat(Status.DELETED)
                .isEqualTo(Status.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue((byte) 99));
    }
}
