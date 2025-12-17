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

package stroom.query.language.functions;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParserAggregates extends AbstractExpressionParserTest {

    @Test
    void testMin1() {
        createGenerator("min(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(300D), storedValues);
            gen.set(Val.of(180D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(180D, Offset.offset(0D));

            gen.set(Val.of(500D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(180D, Offset.offset(0D));

            gen.set(Val.of(600D), storedValues);
            gen.set(Val.of(13D), storedValues);
            gen.set(Val.of(99.3D), storedValues);
            gen.set(Val.of(87D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(13D, Offset.offset(0D));
        });
    }

    @Test
    void testMinUngrouped2() {
        createGenerator("min(${val1}, 100, 30, 8)", (gen, storedValues) -> {
            gen.set(Val.of(300D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testMinGrouped2() {
        createGenerator("min(min(${val1}), 100, 30, 8)", (gen, storedValues) -> {
            gen.set(Val.of(300D), storedValues);
            gen.set(Val.of(180D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testMin3() {
        createGenerator("min(min(${val1}), 100, 30, 8, count(), 55)", (gen, storedValues) -> {
            gen.set(Val.of(300D), storedValues);
            gen.set(Val.of(180D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(Val.of(300D), storedValues);
            gen.set(Val.of(180D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testMax1() {
        createGenerator("max(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(300D), storedValues);
            gen.set(Val.of(180D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(300D, Offset.offset(0D));

            gen.set(Val.of(500D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(500D, Offset.offset(0D));

            gen.set(Val.of(600D), storedValues);
            gen.set(Val.of(13D), storedValues);
            gen.set(Val.of(99.3D), storedValues);
            gen.set(Val.of(87D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(600D, Offset.offset(0D));
        });
    }

    @Test
    void testMaxUngrouped2() {
        createGenerator("max(${val1}, 100, 30, 8)", (gen, storedValues) -> {
            gen.set(Val.of(10D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
        });
    }

    @Test
    void testMaxGrouped2() {
        createGenerator("max(max(${val1}), 100, 30, 8)", (gen, storedValues) -> {
            gen.set(Val.of(10D), storedValues);
            gen.set(Val.of(40D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
        });
    }

    @Test
    void testMax3() {
        createGenerator("max(max(${val1}), count())", (gen, storedValues) -> {
            gen.set(Val.of(3D), storedValues);
            gen.set(Val.of(2D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testSum() {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        createGenerator("sum(${val1}, count())", (gen, storedValues) -> {
            gen.set(Val.of(3D), storedValues);
            gen.set(Val.of(2D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(5D, Offset.offset(0D));
        });
    }

    @Test
    void testSumOfSum() {
        createGenerator("sum(sum(${val1}), count())", (gen, storedValues) -> {
            gen.set(Val.of(3D), storedValues);
            gen.set(Val.of(2D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(11D, Offset.offset(0D));
        });
    }

    @Test
    void testAverageUngrouped() {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        createGenerator("average(${val1}, count())", (gen, storedValues) -> {
            gen.set(Val.of(3D), storedValues);
            gen.set(Val.of(4D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(8D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
        });
    }

    @Test
    void testAverageGrouped() {
        createGenerator("average(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(3D), storedValues);
            gen.set(Val.of(4D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.5D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(8D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testVariance1() {
        createGenerator("variance(600, 470, 170, 430, 300)", (gen, storedValues) -> {
            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
        });
    }

    @Test
    void testVariance2() {
        createGenerator("variance(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(600), storedValues);
            gen.set(Val.of(470), storedValues);
            gen.set(Val.of(170), storedValues);
            gen.set(Val.of(430), storedValues);
            gen.set(Val.of(300), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
        });
    }

    @Test
    void testStDev1() {
        createGenerator("round(stDev(600, 470, 170, 430, 300))", (gen, storedValues) -> {
            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
        });
    }

    @Test
    void testStDev2() {
        createGenerator("round(stDev(${val1}))", (gen, storedValues) -> {
            gen.set(Val.of(600), storedValues);
            gen.set(Val.of(470), storedValues);
            gen.set(Val.of(170), storedValues);
            gen.set(Val.of(430), storedValues);
            gen.set(Val.of(300), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
        });
    }

    @Test
    void testMinDate() {
        createGenerator("min(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(ValDate.create("2023-05-16T01:00:00.000Z")), storedValues);
            gen.set(Val.of(ValDate.create("2023-05-16T04:00:00.000Z")), storedValues);
            gen.set(Val.of(ValDate.create("2023-05-15T04:00:00.000Z")), storedValues);
            gen.set(Val.of(ValDate.create("2023-05-16T05:00:00.000Z")), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.type()).isEqualTo(Type.DATE);
            assertThat(out.toString()).isEqualTo("2023-05-15T04:00:00.000Z");

            gen.set(Val.of(ValDate.create("2023-05-13T04:00:00.000Z")), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.type()).isEqualTo(Type.DATE);
            assertThat(out.toString()).isEqualTo("2023-05-13T04:00:00.000Z");

            gen.set(Val.of(ValDate.create("2023-05-18T04:00:00.000Z")), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.type()).isEqualTo(Type.DATE);
            assertThat(out.toString()).isEqualTo("2023-05-13T04:00:00.000Z");
        });
    }

    @Test
    void testMaxDate() {
        createGenerator("max(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(ValDate.create("2023-05-16T01:00:00.000Z")), storedValues);
            gen.set(Val.of(ValDate.create("2023-05-16T04:00:00.000Z")), storedValues);
            gen.set(Val.of(ValDate.create("2023-05-16T05:00:00.000Z")), storedValues);
            gen.set(Val.of(ValDate.create("2023-05-15T04:00:00.000Z")), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.type()).isEqualTo(Type.DATE);
            assertThat(out.toString()).isEqualTo("2023-05-16T05:00:00.000Z");

            gen.set(Val.of(ValDate.create("2023-05-16T05:00:00.000Z")), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.type()).isEqualTo(Type.DATE);
            assertThat(out.toString()).isEqualTo("2023-05-16T05:00:00.000Z");

            gen.set(Val.of(ValDate.create("2023-05-16T02:00:00.000Z")), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.type()).isEqualTo(Type.DATE);
            assertThat(out.toString()).isEqualTo("2023-05-16T05:00:00.000Z");
        });
    }

}
