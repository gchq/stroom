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

package stroom.pathways.shared.otel.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNanoTime {

    @Test
    void testToString() {
        assertThat(NanoTime.ZERO.toString()).isEqualTo("0ns");
        assertThat(NanoTime.ofNanos(102).toString()).isEqualTo("102ns");
        assertThat(NanoTime.ofMicros(2035).toString()).isEqualTo("2.04ms");
        assertThat(NanoTime.ofMillis(2035).toString()).isEqualTo("2.04s");
        assertThat(NanoTime.ofMillis(2300).toString()).isEqualTo("2.3s");
        assertThat(NanoTime.ofMillis(2000).toString()).isEqualTo("2s");
        assertThat(NanoTime.ofMillis(100).toString()).isEqualTo("100ms");
    }
}
