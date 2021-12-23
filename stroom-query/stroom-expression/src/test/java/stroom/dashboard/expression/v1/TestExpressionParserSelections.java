/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParserSelections extends AbstractExpressionParserTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExpressionParserSelections.class);

    @Test
    void testAny() {
        createGenerator("any(${val1})", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("any(${val1})", child -> {
                    child.set(getVals(300));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toDouble()).isEqualTo(300, Offset.offset(0D));
        });
    }

    @Test
    void testFirst() {
        createGenerator("first(${val1})", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("first(${val1})", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toDouble()).isEqualTo(1, Offset.offset(0D));
        });
    }

    @Test
    void testLast() {
        createGenerator("last(${val1})", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("last(${val1})", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toDouble()).isEqualTo(10, Offset.offset(0D));
        });
    }

    @Test
    void testNth() {
        createGenerator("nth(${val1}, 7)", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("nth(${val1}, 7)", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toDouble()).isEqualTo(7, Offset.offset(0D));
        });
    }

    @Test
    void testTop() {
        createGenerator("top(${val1}, ',', 3)", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("top(${val1}, ',', 3)", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toString()).isEqualTo("1,2,3");
        });
    }

    @Test
    void testTopSmall() {
        createGenerator("top(${val1}, ',', 3)", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[2];
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                createGenerator("top(${val1}, ',', 3)", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toString()).isEqualTo("1,2");
        });
    }

    @Test
    void testBottom() {
        createGenerator("bottom(${val1}, ',', 3)", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("bottom(${val1}, ',', 3)", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toString()).isEqualTo("8,9,10");
        });
    }

    @Test
    void testBottomSmall() {
        createGenerator("bottom(${val1}, ',', 3)", gen -> {
            gen.set(getVals(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[2];
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                createGenerator("bottom(${val1}, ',', 3)", child -> {
                    child.set(getVals(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(createSelection(children));
            assertThat(selected.toString()).isEqualTo("1,2");
        });
    }
}
