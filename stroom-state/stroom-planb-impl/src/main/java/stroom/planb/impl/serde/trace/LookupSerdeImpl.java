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

package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.HashLookupRecorder;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupDb.StaticUnsignedBytesFactory;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.impl.serde.val.VariableValType;
import stroom.planb.shared.HashLength;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class LookupSerdeImpl implements LookupSerde {

    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final int uidLookupThreshold;
    private final UidLookupDb uidLookupDb;
    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;
    private final UnsignedBytes lengthWriter;

    public LookupSerdeImpl(final PlanBEnv env,
                           final HashClashCommitRunnable hashClashCommitRunnable,
                           final ByteBuffers byteBuffers) {
        final HashFactory valueHashFactory = HashFactoryFactory.create(HashLength.INTEGER);
        this.uidLookupDb = new UidLookupDb(
                env,
                byteBuffers,
                "lookup",
                new StaticUnsignedBytesFactory(UnsignedBytesInstances.FOUR));
        this.hashLookupDb = new HashLookupDb(
                env,
                byteBuffers,
                valueHashFactory,
                hashClashCommitRunnable,
                "lookup");

        this.byteBuffers = byteBuffers;
        uidLookupThreshold = 32;
        lengthWriter = UnsignedBytesInstances.forValue(uidLookupThreshold);
    }

    @Override
    public int getStorageLength(final byte[] key) {
        if (key.length > USE_HASH_LOOKUP_THRESHOLD) {
            return key.length + 1;
        } else if (key.length > uidLookupThreshold) {
            return key.length + 1;
        } else {
            return key.length + lengthWriter.length() + 1;
        }
    }

    @Override
    public byte[] read(final Txn<ByteBuffer> txn,
                       final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
        return switch (valType) {
            case DIRECT -> {
                // Read direct.
                final int len = (int) lengthWriter.get(byteBuffer);
                final byte[] bytes = new byte[len];
                byteBuffer.get(bytes);
                yield bytes;
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, getIdSlice(byteBuffer));
                yield ByteBufferUtils.getBytes(valueByteBuffer);
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, getIdSlice(byteBuffer));
                yield ByteBufferUtils.getBytes(valueByteBuffer);
            }
        };
    }

    private static ByteBuffer getIdSlice(final ByteBuffer byteBuffer) {
        final ByteBuffer slice = byteBuffer.slice(byteBuffer.position(), Integer.BYTES);
        ByteBufferUtils.skip(byteBuffer, Integer.BYTES);
        return slice;
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final byte[] key, final ByteBuffer byteBuffer) {
        final int len = getStorageLength(key);

        if (len <= uidLookupThreshold) {
            // Add the variable type prefix to the lookup id.
            byteBuffer.put(VariableValType.DIRECT.getPrimitiveValue());
            lengthWriter.put(byteBuffer, key.length);
            byteBuffer.put(key);

        } else {
            byteBuffers.useBytes(key, valueByteBuffer -> {
                if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                    hashLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                        assert idByteBuffer.remaining() == 4;
                        // Add the variable type prefix to the lookup id.
                        byteBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                        byteBuffer.put(idByteBuffer);
                        return null;
                    });
                } else {
                    uidLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                        assert idByteBuffer.remaining() == 4;
                        // Add the variable type prefix to the lookup id.
                        byteBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                        byteBuffer.put(idByteBuffer);
                        return null;
                    });
                }
                return null;
            });
        }
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
        return switch (valType) {
            case DIRECT -> {
                // Read direct.
                final int len = (int) lengthWriter.get(byteBuffer);
                ByteBufferUtils.skip(byteBuffer, len);
                yield false;
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                getIdSlice(byteBuffer);
                yield true;
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                getIdSlice(byteBuffer);
                yield true;
            }
        };
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UsedLookupsRecorderImpl(env, uidLookupDb, hashLookupDb, lengthWriter);
    }

    private static class UsedLookupsRecorderImpl implements UsedLookupsRecorder {

        private final UidLookupRecorder uidLookupRecorder;
        private final HashLookupRecorder hashLookupRecorder;
        private final UnsignedBytes lengthWriter;

        public UsedLookupsRecorderImpl(final PlanBEnv env,
                                       final UidLookupDb uidLookupDb,
                                       final HashLookupDb hashLookupDb,
                                       final UnsignedBytes lengthWriter) {
            uidLookupRecorder = new UidLookupRecorder(env, uidLookupDb);
            hashLookupRecorder = new HashLookupRecorder(env, hashLookupDb);
            this.lengthWriter = lengthWriter;
        }

        @Override
        public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
            // Read the variable type.
            final VariableValType valType =
                    VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
            switch (valType) {
                case DIRECT -> {
                    // Read direct.
                    final int len = (int) lengthWriter.get(byteBuffer);
                    ByteBufferUtils.skip(byteBuffer, len);
                }
                case UID_LOOKUP -> {
                    // Read via UI lookup.
                    uidLookupRecorder.recordUsed(writer, getIdSlice(byteBuffer));
                }
                case HASH_LOOKUP -> {
                    // Read via hash lookup.
                    hashLookupRecorder.recordUsed(writer, getIdSlice(byteBuffer));
                }
            }
            ;
        }

        @Override
        public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
            uidLookupRecorder.deleteUnused(readTxn, writer);
            hashLookupRecorder.deleteUnused(readTxn, writer);
        }
    }
}
