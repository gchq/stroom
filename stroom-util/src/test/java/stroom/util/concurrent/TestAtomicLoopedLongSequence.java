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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAtomicLoopedLongSequence {

    @Test
    void getNext1() {
        final AtomicLoopedLongSequence sequence = new AtomicLoopedLongSequence(5);
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 5; i++) {
                Assertions.assertThat(sequence.getNext())
                        .isEqualTo(i);
            }
        }
    }

    @Test
    void getNext2() {
        final AtomicLoopedLongSequence sequence = new AtomicLoopedLongSequence(1, 6);
        for (int j = 0; j < 2; j++) {
            for (int i = 1; i < 6; i++) {
                Assertions.assertThat(sequence.getNext())
                        .isEqualTo(i);
            }
        }
    }
}
