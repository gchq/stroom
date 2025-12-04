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

package stroom.util.shared;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRange {

    @Test
    void contains() {
        assertThat(Range.of(10L, 20L).contains(9)).isFalse();
        assertThat(Range.of(10L, 20L).contains(10)).isTrue();
        assertThat(Range.of(10L, 20L).contains(15)).isTrue();
        assertThat(Range.of(10L, 20L).contains(19)).isTrue();
        assertThat(Range.of(10L, 20L).contains(20)).isFalse();
    }

    @Test
    void after() {
        assertThat(Range.of(10L, 20L).after(9)).isTrue();
        assertThat(Range.of(10L, 20L).after(10)).isFalse();
        assertThat(Range.of(10L, 20L).after(15)).isFalse();
        assertThat(Range.of(10L, 20L).after(19)).isFalse();
        assertThat(Range.of(10L, 20L).after(20)).isFalse();
    }

    @Test
    void before() {
        assertThat(Range.of(10L, 20L).before(9)).isFalse();
        assertThat(Range.of(10L, 20L).before(10)).isFalse();
        assertThat(Range.of(10L, 20L).before(15)).isFalse();
        assertThat(Range.of(10L, 20L).before(19)).isFalse();
        assertThat(Range.of(10L, 20L).before(20L)).isTrue();
    }

    @Test
    void isBounded() {
        assertThat(Range.of(10L, 20L).isBounded()).isTrue();
        assertThat(Range.from(10L).isBounded()).isFalse();
        assertThat(Range.to(20L).isBounded()).isFalse();
        assertThat(new Range<Long>().isBounded()).isFalse();
    }

    @Test
    void isConstrained() {
        assertThat(Range.of(10L, 20L).isConstrained()).isTrue();
        assertThat(Range.from(10L).isConstrained()).isTrue();
        assertThat(Range.to(20L).isConstrained()).isTrue();
        assertThat(new Range<Long>().isConstrained()).isFalse();
    }
}
