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

package stroom.lmdb.serde;

import stroom.lmdb.UnSortedDupKey;
import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

class TestUnSortedDupKeySerde
        extends AbstractSerdeTest<
        UnSortedDupKey<String>,
        UnSortedDupKeySerde<String>> {

    private static final int ID_BYTE_LENGTH = 4;

    @Test
    void serializeDeserialize() {
        final String key = "foo";
        final UnsortedDupKeyFactory<String> keyFactory = UnSortedDupKey.createFactory(
                String.class, ID_BYTE_LENGTH);
        final UnSortedDupKey<String> unSortedDupKey = keyFactory.createUnsortedKey(key);

        doSerialisationDeserialisationTest(unSortedDupKey);
    }

    @Override
    Supplier<UnSortedDupKeySerde<String>> getSerdeSupplier() {
        return () -> new UnSortedDupKeySerde<>(new StringSerde(), ID_BYTE_LENGTH);
    }

    @Override
    TypeLiteral<UnSortedDupKeySerde<String>> getSerdeType() {
        return new TypeLiteral<UnSortedDupKeySerde<String>>(){};
    }
}
