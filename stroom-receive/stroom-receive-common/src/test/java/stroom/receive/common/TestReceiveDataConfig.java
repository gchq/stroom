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

package stroom.receive.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestReceiveDataConfig {

    @Test
    void testBuilder() {
        // Attempt to check that the no-args ctor produces the same default config
        // as the builder with no modifications. Relies on equals method being
        // up-to-date though
        final ReceiveDataConfig receiveDataConfig1 = new ReceiveDataConfig();
        final ReceiveDataConfig receiveDataConfig2 = ReceiveDataConfig.builder()
                .build();

        Assertions.assertThat(receiveDataConfig1)
                .isEqualTo(receiveDataConfig2);
    }
}
