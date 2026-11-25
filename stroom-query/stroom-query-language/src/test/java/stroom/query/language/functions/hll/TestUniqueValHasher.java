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

package stroom.query.language.functions.hll;

import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValByte;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValPeriod;
import stroom.query.language.functions.ValShort;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValXml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUniqueValHasher {

    // Hashes are stored and are sent between nodes, so a failure here means the stored format has changed.
    // These were worked out with xxhsum -H3 from the bytes each value is written as.
    static Stream<Arguments> goldenHashes() {
        return Stream.of(
                Arguments.of(ValNull.INSTANCE, 0xc44bdff4074eecdbL),
                Arguments.of(ValBoolean.create(true), 0x070d80d6a76865f3L),
                Arguments.of(ValFloat.create(1.5F), 0x6837db10039c5c70L),
                Arguments.of(ValDouble.create(1.5D), 0x58bdc64af8b48dc4L),
                Arguments.of(ValInteger.create(1), 0x7b2778a4c6f9151fL),
                Arguments.of(ValLong.create(1), 0xd8ab8df0ba7704d7L),
                Arguments.of(ValDate.create(1_790_000_000_000L), 0x6e5fd7499dec5ce0L),
                Arguments.of(ValString.create("1"), 0x15521b14c85d67d1L),
                Arguments.of(ValString.create("user1"), 0x98c3a7e82cf38a65L),
                Arguments.of(ValString.create("h" + Character.toString(0xE9)), 0x60cde8bda7db84bdL),
                Arguments.of(ValString.create("x".repeat(5_000)), 0xf72e5f3be4232107L),
                Arguments.of(ValErr.create("bad"), 0x7e9aa180a808cdccL),
                Arguments.of(ValDuration.create(1_000L), 0x8da8ce49a815e469L),
                Arguments.of(ValPeriod.create(1, 2, 3), 0x21b5cad474393b16L),
                Arguments.of(ValByte.create((byte) 1), 0x75860af25bb3501bL),
                Arguments.of(ValShort.create((short) 1), 0x78eef5bc1f3ad3f9L),
                Arguments.of(ValXml.create(new byte[]{1, 2, 3}), 0xe9f3dcfa643c8824L));
    }

    @ParameterizedTest
    @MethodSource("goldenHashes")
    void testGoldenHash(final Val val, final long expected) {
        assertThat(UniqueValHasher.hash(val))
                .as("%016x", UniqueValHasher.hash(val))
                .isEqualTo(expected);
    }

    @Test
    void testGoldenHashesCoverAllTypes() {
        assertThat(goldenHashes().map(arguments -> ((Val) arguments.get()[0]).type()))
                .containsOnly(Type.values());
    }

    // Values must have the same hash if, and only if, they are equal.
    @Test
    void testHashFollowsEquals() {
        final List<Val> vals = vals();
        final List<Val> sameVals = vals();
        for (int i = 0; i < vals.size(); i++) {
            for (int j = 0; j < vals.size(); j++) {
                final Val left = vals.get(i);
                final Val right = sameVals.get(j);
                assertThat(UniqueValHasher.hash(left) == UniqueValHasher.hash(right))
                        .as("%s %s and %s %s", left.type(), left, right.type(), right)
                        .isEqualTo(left.equals(right));
            }
        }
    }

    @Test
    void testDifferentNaNsAreEqual() {
        final Val nan = ValDouble.create(Double.NaN);
        final Val otherNan = ValDouble.create(Double.longBitsToDouble(0x7ff8000000000001L));
        assertThat(otherNan).isEqualTo(nan);
        assertThat(UniqueValHasher.hash(otherNan)).isEqualTo(UniqueValHasher.hash(nan));

        final Val floatNan = ValFloat.create(Float.NaN);
        final Val otherFloatNan = ValFloat.create(Float.intBitsToFloat(0x7fc00001));
        assertThat(otherFloatNan).isEqualTo(floatNan);
        assertThat(UniqueValHasher.hash(otherFloatNan)).isEqualTo(UniqueValHasher.hash(floatNan));
    }

    @Test
    void testLongStringsAreNotCut() {
        final Set<Long> hashes = new HashSet<>();
        for (final int length : new int[]{63, 64, 65, 1_000, 1_001, 10_000, 1_000_000}) {
            hashes.add(UniqueValHasher.hash(ValString.create("a".repeat(length))));
            hashes.add(UniqueValHasher.hash(ValString.create("a".repeat(length - 1) + "b")));
        }
        assertThat(hashes).hasSize(14);
    }

    @Test
    void testNull() {
        assertThatThrownBy(() -> UniqueValHasher.hash(null))
                .isInstanceOf(NullPointerException.class);
    }

    // The sketch needs the hash to be evenly spread, even for values that are nearly the same.
    @Test
    void testSpread() {
        assertSpread(ValLong::create);
        assertSpread(ValInteger::create);
        assertSpread(i -> ValLong.create((long) i << 32));
        assertSpread(i -> ValDate.create(1_790_000_000_000L + i * 1000L));
        assertSpread(ValDouble::create);
        assertSpread(i -> ValString.create(String.valueOf(i)));
        assertSpread(i -> ValString.create("user" + i + "@example.com"));
        assertSpread(i -> ValString.create("10." + (i >> 16) + "." + ((i >> 8) & 0xFF) + "." + (i & 0xFF)));
        assertSpread(i -> ValString.create("x".repeat(2_000) + i));
    }

    private static void assertSpread(final IntFunction<Val> function) {
        final int precision = 14;
        final double standardError = 1.04 / Math.sqrt(1 << precision);
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
        final Map<Long, Val> seen = new HashMap<>();
        int n = 0;
        for (final int limit : new int[]{1_000, 30_000, 200_000}) {
            while (n < limit) {
                final Val val = function.apply(n);
                final long hash = UniqueValHasher.hash(val);
                assertThat(seen.put(hash, val)).isNull();
                sketch.addHash(hash);
                n++;
            }
            assertThat((double) (sketch.estimate() - n) / n)
                    .as("%s n=%s", function.apply(0).type(), n)
                    .isBetween(-3 * standardError, 3 * standardError);
        }
    }

    private static List<Val> vals() {
        return Arrays.asList(
                ValNull.INSTANCE,
                ValBoolean.create(false),
                ValBoolean.create(true),
                ValByte.create((byte) 1),
                ValShort.create((short) 1),
                ValInteger.create(0),
                ValInteger.create(1),
                ValInteger.create(-1),
                ValInteger.create(Integer.MAX_VALUE),
                ValLong.create(0),
                ValLong.create(1),
                ValLong.create(-1),
                ValLong.create(Long.MIN_VALUE),
                ValFloat.create(1F),
                ValDouble.create(1D),
                ValDouble.create(0D),
                ValDouble.create(-0D),
                ValDouble.create(Double.NaN),
                ValDate.create(1L),
                ValDuration.create(1L),
                ValPeriod.create(0, 0, 0),
                ValPeriod.create(0, 0, 1),
                ValPeriod.create(0, 1, 0),
                ValPeriod.create(0, 12, 0),
                ValPeriod.create(1, 0, 0),
                ValPeriod.create(-1, 0, 0),
                ValString.create(""),
                ValString.create("1"),
                ValString.create("a"),
                ValString.create("A"),
                ValString.create("a "),
                ValString.create(Character.toString(0xE9)),
                ValString.create("e" + Character.toString(0x301)),
                ValString.create(Character.toString(0x1F600)),
                ValErr.create("a"),
                ValXml.create("a".getBytes(StandardCharsets.UTF_8)));
    }
}
