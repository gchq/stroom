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

package stroom.util.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TestAtomicBitMask {

    @Nested
    @DisplayName("Constructor and Initialization Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor should create mask of size 64")
        void defaultConstructor() {
            final AtomicBitMask mask = new AtomicBitMask();

            // We can infer size by checking we can set the last bit (63)
            // and that it starts empty
            assertThat(mask.asLong()).isZero();
            assertThat(mask.countSetBits()).isZero();

            // Should not throw on max index
            assertThatCode(() -> mask.isSet(63)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Constructor with valid size should initialize correctly")
        void validSizeConstructor() {
            final int size = 10;
            final AtomicBitMask mask = new AtomicBitMask(size);

            assertThat(mask.asLong()).isZero();
            assertThat(mask.countUnSetBits()).isEqualTo(size);
        }

        @Test
        @DisplayName("Constructor should throw exception for size < 1")
        void constructorTooSmall() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AtomicBitMask(0))
                    .withMessageContaining("size must be between 1 and 64");
        }

        @Test
        @DisplayName("Constructor should throw exception for size > 64")
        void constructorTooLarge() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AtomicBitMask(65))
                    .withMessageContaining("size must be between 1 and 64");
        }
    }

    @Nested
    @DisplayName("Bit Manipulation Tests")
    class ManipulationTests {

        @Test
        @DisplayName("setAndGetAsLong should set specific bit and return new value")
        void setAndGetAsLong() {
            final AtomicBitMask mask = new AtomicBitMask(10);

            // Set bit 0 -> Value 1
            final long val1 = mask.setAndGetAsLong(0);
            assertThat(val1).isEqualTo(1L);
            assertThat(mask.isSet(0)).isTrue();

            // Set bit 2 -> Value 1 | 4 = 5
            final long val2 = mask.setAndGetAsLong(2);
            assertThat(val2).isEqualTo(5L);
            assertThat(mask.isSet(2)).isTrue();
        }

        @Test
        @DisplayName("setAndGetAsLong is idempotent")
        void setAndGetIdempotent() {
            final AtomicBitMask mask = new AtomicBitMask();
            mask.setAndGetAsLong(5);

            final long result = mask.setAndGetAsLong(5);

            assertThat(mask.isSet(5)).isTrue();
            // Value should remain just bit 5 set (32)
            assertThat(result).isEqualTo(1L << 5);
        }

        @Test
        @DisplayName("getAsLongAndSet should set bit but return previous value")
        void getAsLongAndSet() {
            final AtomicBitMask mask = new AtomicBitMask();

            // Initially 0. Set bit 1 (val 2). Return should be 0.
            long prev = mask.getAsLongAndSet(1);
            assertThat(prev).isZero();
            assertThat(mask.isSet(1)).isTrue();

            // Currently 2. Set bit 1 again. Return should be 2.
            prev = mask.getAsLongAndSet(1);
            assertThat(prev).isEqualTo(2L);
        }

        @Test
        @DisplayName("unset should clear bit and return new value")
        void unset() {
            final AtomicBitMask mask = new AtomicBitMask();
            mask.setAndGetAsLong(0);
            mask.setAndGetAsLong(1);
            // Current value is 3 (11 binary)

            final long result = mask.unset(0);

            assertThat(result).isEqualTo(2L); // Only bit 1 remains
            assertThat(mask.isSet(0)).isFalse();
            assertThat(mask.isSet(1)).isTrue();
        }

        @Test
        @DisplayName("flip should toggle bit state")
        void flip() {
            final AtomicBitMask mask = new AtomicBitMask();

            // 0 -> 1
            boolean result = mask.flip(5);
            assertThat(result).isTrue();
            assertThat(mask.isSet(5)).isTrue();

            // 1 -> 0
            result = mask.flip(5);
            assertThat(result).isFalse();
            assertThat(mask.isSet(5)).isFalse();
        }

        @Test
        @DisplayName("setAndGetAsLong(idx, boolean) should dispatch correctly")
        void setAndGetBoolean() {
            final AtomicBitMask mask = new AtomicBitMask();

            // Set true
            mask.setAndGetAsLong(3, true);
            assertThat(mask.isSet(3)).isTrue();

            // Set false
            mask.setAndGetAsLong(3, false);
            assertThat(mask.isSet(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("Bulk Operation Tests")
    class BulkTests {

        @Test
        @DisplayName("setAll should set every bit for MAX_SIZE")
        void setAllMaxSize() {
            final AtomicBitMask mask = new AtomicBitMask(64);
            mask.setAll();

            assertThat(mask.asLong()).isEqualTo(-1L); // All 1s in 64-bit signed long is -1
            assertThat(mask.countSetBits()).isEqualTo(64);
            assertThat(mask.countUnSetBits()).isZero();
        }

        @Test
        @DisplayName("setAll should set only bits up to size for smaller masks")
        void setAllSmallSize() {
            // Size 3 -> Bits 0, 1, 2 -> 1 + 2 + 4 = 7
            final AtomicBitMask mask = new AtomicBitMask(3);
            mask.setAll();

            assertThat(mask.asLong()).isEqualTo(7L);
            assertThat(mask.countSetBits()).isEqualTo(3);
        }

        @Test
        @DisplayName("unSetAll should clear all bits")
        void unSetAll() {
            final AtomicBitMask mask = new AtomicBitMask();
            mask.setAll();
            mask.unSetAll();

            assertThat(mask.asLong()).isZero();
            assertThat(mask.countSetBits()).isZero();
        }
    }

    @Nested
    @DisplayName("Query and Counting Tests")
    class QueryTests {

        @Test
        @DisplayName("countSetBits returns correct population count")
        void countSetBits() {
            final AtomicBitMask mask = new AtomicBitMask();
            mask.setAndGetAsLong(0);
            mask.setAndGetAsLong(10);
            mask.setAndGetAsLong(20);

            assertThat(mask.countSetBits()).isEqualTo(3);
        }

        @Test
        @DisplayName("countUnSetBits returns correct difference")
        void countUnSetBits() {
            final int size = 10;
            final AtomicBitMask mask = new AtomicBitMask(size);
            mask.setAndGetAsLong(1);
            mask.setAndGetAsLong(2);

            assertThat(mask.countUnSetBits()).isEqualTo(8); // 10 - 2
        }

        @Test
        @DisplayName("Static count utility methods work as expected")
        void staticCountUtils() {
            final AtomicBitMask mask = new AtomicBitMask(64);
            final long val = 7L; // (111 binary)

            assertThat(mask.countSetBits(val)).isEqualTo(3);
            assertThat(mask.countUnSetBits(val)).isEqualTo(61); // 64 - 3
        }

        @Test
        @DisplayName("isSet(long, idx) checks bits on arbitrary value")
        void isSetExternal() {
            final AtomicBitMask mask = new AtomicBitMask();
            final long val = 2L; // 10 binary, bit 1 is set

            assertThat(mask.isSet(val, 1)).isTrue();
            assertThat(mask.isSet(val, 0)).isFalse();
        }

        @Test
        @DisplayName("toString returns binary string representation")
        void toStringCheck() {
            final AtomicBitMask mask = new AtomicBitMask();
            mask.setAndGetAsLong(0);
            mask.setAndGetAsLong(2);

            // 2^0 + 2^2 = 1 + 4 = 5. Binary "101"
            assertThat(mask.toString()).isEqualTo("101");
        }
    }

    @Nested
    @DisplayName("Boundary and Validation Tests")
    class BoundaryTests {

        @ParameterizedTest
        @ValueSource(ints = {-1, 10, 100})
        @DisplayName("Operations should throw exception when index is out of bounds")
        void indexOutOfBounds(final int invalidIndex) {
            final AtomicBitMask mask = new AtomicBitMask(10); // Max index is 9

            assertThatIllegalArgumentException().isThrownBy(() -> mask.flip(invalidIndex));
            assertThatIllegalArgumentException().isThrownBy(() -> mask.setAndGetAsLong(invalidIndex));
            assertThatIllegalArgumentException().isThrownBy(() -> mask.unset(invalidIndex));
            assertThatIllegalArgumentException().isThrownBy(() -> mask.isSet(invalidIndex));
        }

        @Test
        @DisplayName("Boundary: Can set the very last bit (size - 1)")
        void lastBitBoundary() {
            final int size = 10;
            final AtomicBitMask mask = new AtomicBitMask(size);

            mask.setAndGetAsLong(9); // Index 9 is valid for size 10

            assertThat(mask.isSet(9)).isTrue();
        }

        @Test
        @DisplayName("Boundary: Cannot set bit equal to size")
        void sizeBitBoundary() {
            final int size = 10;
            final AtomicBitMask mask = new AtomicBitMask(size);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> mask.setAndGetAsLong(10))
                    .withMessageContaining("idx must be between 0 and 9");
        }
    }
}
