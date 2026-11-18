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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueMasker {

    @Test
    void testMask() {
        assertThat(ValueMasker.mask("Login failed for 10.0.0.1 after 3 tries"))
                .isEqualTo("Login failed for <ip> after <n> tries");
        assertThat(ValueMasker.mask("at 2026-10-15T10:00:01.123Z id 3f2a9c1e-1b2c-4d5e-8f90-123456789abc"))
                .isEqualTo("at <time> id <uuid>");
        assertThat(ValueMasker.mask("session deadbeef01 took 12:01:02")).isEqualTo("session <hex> took <time>");
        assertThat(ValueMasker.mask("order 123456789")).isEqualTo("order <n>");
        assertThat(ValueMasker.mask("")).isEmpty();
        assertThat(ValueMasker.mask(null)).isNull();
    }
}
