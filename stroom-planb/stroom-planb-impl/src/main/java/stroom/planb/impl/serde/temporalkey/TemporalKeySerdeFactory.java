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

package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.PlanBDoc;

public class TemporalKeySerdeFactory {

    private static final String KEY_LOOKUP_DB_NAME = "key";

    public static TemporalKeySerde createKeySerde(final PlanBDoc doc,
                                                  final KeyType keyType,
                                                  final HashLength hashLength,
                                                  final PlanBEnv env,
                                                  final ByteBuffers byteBuffers,
                                                  final TimeSerde timeSerde,
                                                  final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (keyType) {
            case BOOLEAN -> new BooleanKeySerde(byteBuffers, timeSerde);
            case BYTE -> new ByteKeySerde(byteBuffers, timeSerde);
            case SHORT -> new ShortKeySerde(byteBuffers, timeSerde);
            case INT -> new IntegerKeySerde(byteBuffers, timeSerde);
            case LONG -> new LongKeySerde(byteBuffers, timeSerde);
            case FLOAT -> new FloatKeySerde(byteBuffers, timeSerde);
            case DOUBLE -> new DoubleKeySerde(byteBuffers, timeSerde);
            case STRING -> new LimitedStringKeySerde(byteBuffers, timeSerde);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new UidLookupKeySerde(uidLookupDb, byteBuffers, timeSerde);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new HashLookupKeySerde(hashLookupDb, byteBuffers, timeSerde);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new VariableKeySerde(doc, uidLookupDb, hashLookupDb, byteBuffers, timeSerde);
            }
            case TAGS -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new TagsKeySerde(uidLookupDb, byteBuffers, timeSerde);
            }
        };
    }
}
