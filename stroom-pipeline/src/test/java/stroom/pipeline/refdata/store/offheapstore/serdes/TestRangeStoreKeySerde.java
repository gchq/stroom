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

package stroom.pipeline.refdata.store.offheapstore.serdes;


import stroom.pipeline.refdata.store.offheapstore.RangeStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.util.shared.Range;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestRangeStoreKeySerde extends AbstractSerdeTest<RangeStoreKey, RangeStoreKeySerde> {

    @Test
    void testSerialiseDeserialise() {
        final UID uid = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 1, 2, 3);
        final Range<Long> range = new Range<>(23L, 52L);

        final RangeStoreKey rangeStoreKey = new RangeStoreKey(uid, range);

        doSerialisationDeserialisationTest(rangeStoreKey);
    }

    @Test
    void testCopyWithNewUid() {
        final UID uid1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 1, 2, 3);
        final Range<Long> range = new Range<>(23L, 52L);

        final RangeStoreKey rangeStoreKey = new RangeStoreKey(uid1, range);
        final UID uid2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 4, 5, 6, 7);

        final ByteBuffer sourceBuffer = ByteBuffer.allocateDirect(200);
        getSerde().serialize(sourceBuffer, rangeStoreKey);
        final ByteBuffer destBuffer = ByteBuffer.allocateDirect(200);

        RangeStoreKeySerde.copyWithNewUid(sourceBuffer, destBuffer, uid2);

        final RangeStoreKey rangeStoreKey2 = getSerde().deserialize(destBuffer);

        assertThat(rangeStoreKey2.getKeyRange())
                .isEqualTo(rangeStoreKey.getKeyRange());
        assertThat(rangeStoreKey2.getMapUid())
                .isEqualTo(uid2);
    }

    @Override
    TypeLiteral<RangeStoreKeySerde> getSerdeType() {
        return new TypeLiteral<RangeStoreKeySerde>(){};
    }
}
