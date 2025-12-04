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

package stroom.util.concurrent;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAtomicSequence {

    @Test
    void testSimple() {
        final AtomicSequence atomicSequence = new AtomicSequence(3);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(1);
        assertThat(atomicSequence.next()).isEqualTo(2);
        assertThat(atomicSequence.next()).isEqualTo(0);
    }

    @Test
    void testCallLimit1() {
        final AtomicSequence atomicSequence = new AtomicSequence(1);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(0);
    }

    @Test
    void testCallLimit2() {
        final AtomicSequence atomicSequence = new AtomicSequence();
        assertThat(atomicSequence.next(2)).isEqualTo(0);
        assertThat(atomicSequence.next(2)).isEqualTo(1);
        assertThat(atomicSequence.next(2)).isEqualTo(0);
        assertThat(atomicSequence.next(2)).isEqualTo(1);
        assertThat(atomicSequence.next(2)).isEqualTo(0);
    }

    @Test
    void testCallLimit3() {
        final AtomicSequence atomicSequence = new AtomicSequence(3);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(1);
        assertThat(atomicSequence.next()).isEqualTo(2);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(1);
        assertThat(atomicSequence.next()).isEqualTo(2);
        assertThat(atomicSequence.next()).isEqualTo(0);
        assertThat(atomicSequence.next()).isEqualTo(1);
        assertThat(atomicSequence.next()).isEqualTo(2);
    }
}
