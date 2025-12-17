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

package stroom.lmdb.serde;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnsignedLong {

    @Test
    void increment1() {
        final UnsignedLong unsignedLong = UnsignedLong.of(0, 4);
        assertThat(unsignedLong.getValue())
                .isEqualTo(0);
        final UnsignedLong newVal = unsignedLong.increment();
        assertThat(newVal.getValue())
                .isEqualTo(1);
    }

    @Test
    void increment2() {
        final UnsignedLong unsignedLong = UnsignedLong.of(0, 4);
        assertThat(unsignedLong.getValue())
                .isEqualTo(0);
        final UnsignedLong newVal = unsignedLong.increment(5);
        assertThat(newVal.getValue())
                .isEqualTo(5);
    }

    @Test
    void increment3() {
        final int byteLen = 4;
        final long maxVal = UnsignedBytesInstances.ofLength(byteLen).getMaxVal();
        final UnsignedLong unsignedLong = UnsignedLong.of(maxVal, byteLen);
        assertThat(unsignedLong.getValue())
                .isEqualTo(maxVal);
        Assertions.assertThatThrownBy(unsignedLong::increment)
                        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decrement1() {
        final UnsignedLong unsignedLong = UnsignedLong.of(1, 4);
        assertThat(unsignedLong.getValue())
                .isEqualTo(1);
        final UnsignedLong newVal = unsignedLong.decrement();
        assertThat(newVal.getValue())
                .isEqualTo(0);
    }

    @Test
    void decrement2() {
        final UnsignedLong unsignedLong = UnsignedLong.of(5, 4);
        assertThat(unsignedLong.getValue())
                .isEqualTo(5);
        final UnsignedLong newVal = unsignedLong.decrement(5);
        assertThat(newVal.getValue())
                .isEqualTo(0);
    }

    @Test
    void decrement3() {
        final UnsignedLong unsignedLong = UnsignedLong.of(0, 4);
        assertThat(unsignedLong.getValue())
                .isEqualTo(0);
        Assertions.assertThatThrownBy(unsignedLong::decrement)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
