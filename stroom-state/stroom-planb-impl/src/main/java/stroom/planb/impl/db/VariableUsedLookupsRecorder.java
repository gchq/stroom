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

package stroom.planb.impl.db;

import stroom.planb.impl.serde.val.VariableValType;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class VariableUsedLookupsRecorder implements UsedLookupsRecorder {

    private final UidLookupRecorder uidLookupRecorder;
    private final HashLookupRecorder hashLookupRecorder;

    public VariableUsedLookupsRecorder(final PlanBEnv env,
                                       final UidLookupDb uidLookupDb,
                                       final HashLookupDb hashLookupDb) {
        uidLookupRecorder = new UidLookupRecorder(env, uidLookupDb);
        hashLookupRecorder = new HashLookupRecorder(env, hashLookupDb);
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER
                .fromPrimitiveValue(byteBuffer.get());
        switch (valType) {
            case UID_LOOKUP -> uidLookupRecorder.recordUsed(writer, byteBuffer);
            case HASH_LOOKUP -> hashLookupRecorder.recordUsed(writer, byteBuffer);
            default -> {
                // Do nothing.
            }
        }
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        uidLookupRecorder.deleteUnused(readTxn, writer);
        hashLookupRecorder.deleteUnused(readTxn, writer);
    }
}
