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

package stroom.planb.impl.serde.valtime;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateValueType;

public class ValTimeSerdeFactory {
    private static final String VALUE_LOOKUP_DB_NAME = "value";

    public static ValTimeSerde createValueSerde(final StateValueType stateValueType,
                                                 final HashLength hashLength,
                                                 final PlanBEnv env,
                                                 final ByteBuffers byteBuffers,
                                                 final HashClashCommitRunnable hashClashCommitRunnable) {
        final InsertTimeSerde timeSerde = new InsertTimeSerde();
        return switch (stateValueType) {
            case BOOLEAN -> new BooleanValTimeSerde(timeSerde);
            case BYTE -> new ByteValTimeSerde(timeSerde);
            case SHORT -> new ShortValTimeSerde(timeSerde);
            case INT -> new IntegerValTimeSerde(timeSerde);
            case LONG -> new LongValTimeSerde(timeSerde);
            case FLOAT -> new FloatValTimeSerde(timeSerde);
            case DOUBLE -> new DoubleValTimeSerde(timeSerde);
            case STRING -> new StringValTimeSerde(byteBuffers, timeSerde);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        VALUE_LOOKUP_DB_NAME);
                yield new UidLookupValTimeSerde(uidLookupDb, byteBuffers, timeSerde);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        VALUE_LOOKUP_DB_NAME);
                yield new HashLookupValTimeSerde(hashLookupDb, byteBuffers, timeSerde);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        VALUE_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        VALUE_LOOKUP_DB_NAME);
                yield new VariableValTimeSerde(uidLookupDb, hashLookupDb, byteBuffers, timeSerde);
            }
        };
    }
}
