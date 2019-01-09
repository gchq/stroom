/*
 * Copyright 2016 Crown Copyright
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestSimpleConcurrentMap {
    @Test
    void testSimple() {
        ExampleSimpleConcurrentMap test = new ExampleSimpleConcurrentMap();

        assertThat(test.get("TEST").get()).isEqualTo(0);
        assertThat(test.get("TEST").incrementAndGet()).isEqualTo(1);
        assertThat(test.get("TEST").incrementAndGet()).isEqualTo(2);

        assertThat(test.keySet().size()).isEqualTo(1);
        assertThat(test.get("TEST1").incrementAndGet()).isEqualTo(1);
        assertThat(test.keySet().size()).isEqualTo(2);
    }

    private static class ExampleSimpleConcurrentMap extends SimpleConcurrentMap<String, AtomicInteger> {
        @Override
        protected AtomicInteger initialValue(final String key) {
            return new AtomicInteger(0);
        }
    }

}
