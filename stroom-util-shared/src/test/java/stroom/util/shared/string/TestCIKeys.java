/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.shared.string;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestCIKeys {

    @Test
    void test() {
        // ACCEPT is a common key, so any way we get/create it should give us the same string instances.
        final CIKey key1 = CIKeys.ACCEPT;
        final CIKey key2 = CIKey.of(key1.get());
        final CIKey key3 = CIKey.ofLowerCase(key1.getAsLowerCase());
        final CIKey key4 = CIKey.of("accept");
        // Explicitly don't try to get a common key
        final CIKey dynamicKey1 = CIKey.ofDynamicKey("accept");
        final CIKey dynamicKey2 = CIKey.ofDynamicKey("accept");

        Assertions.assertThat(key1)
                .isSameAs(key2);
        Assertions.assertThat(key1)
                .isSameAs(key3);
        Assertions.assertThat(key1)
                .isSameAs(key4);

        // Different instances
        Assertions.assertThat(key1)
                .isNotSameAs(dynamicKey1);
        Assertions.assertThat(key1)
                .isNotSameAs(dynamicKey2);
        Assertions.assertThat(dynamicKey1)
                .isNotSameAs(dynamicKey2);

        Assertions.assertThat(key1.get())
                .isSameAs(key2.get());
        Assertions.assertThat(key1.get())
                .isSameAs(key3.get());
        Assertions.assertThat(key1.get())
                .isSameAs(key4.get());

        Assertions.assertThat(key1.getAsLowerCase())
                .isSameAs(key2.getAsLowerCase());
        Assertions.assertThat(key1.getAsLowerCase())
                .isSameAs(key3.getAsLowerCase());
        Assertions.assertThat(key1.getAsLowerCase())
                .isSameAs(key4.getAsLowerCase());
    }
}
