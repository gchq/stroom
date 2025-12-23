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

package stroom.data.store.impl.fs.s3v2;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdDictionary {

    @Test
    void testBadUuid() {
        final UUID zeroUuid = new UUID(0, 0);

        Assertions.assertThatThrownBy(
                        () -> new ZstdDictionary(zeroUuid, new byte[10]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGoodUuid() {
        final UUID uuid = UUID.randomUUID();
        final byte[] bytes = {-23, 45, 28, 23};
        final ZstdDictionary zstdDictionary = new ZstdDictionary(uuid, bytes);
        assertThat(zstdDictionary.getUuid())
                .isEqualTo(uuid);
        assertThat(zstdDictionary.getDictionaryBytes())
                .isEqualTo(bytes);
        assertThat(zstdDictionary.size())
                .isEqualTo(bytes.length);
    }
}
