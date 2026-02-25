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

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class UidLookupRecorder implements UsedLookupsRecorder {

    private final UsedLookupsDb usedLookupsDb;
    private final UidLookupDb uidLookupDb;

    public UidLookupRecorder(final PlanBEnv env,
                             final UidLookupDb uidLookupDb) {
        this.usedLookupsDb = new UsedLookupsDb(env, uidLookupDb.getName() + "-UidLookupDb");
        this.uidLookupDb = uidLookupDb;
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        usedLookupsDb.record(writer, byteBuffer);
    }

    public void recordUsed(final LmdbWriter writer, final long uid) {
        uidLookupDb.uidToByteBuffer(uid, byteBuffer -> {
            usedLookupsDb.record(writer, byteBuffer);
            return null;
        });
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        uidLookupDb.forEachUid(readTxn, uid -> {
            if (usedLookupsDb.isUnused(writer.getWriteTxn(), uid)) {
                uidLookupDb.deleteByUid(writer.getWriteTxn(), uid);
                writer.tryCommit();
            }
        });
        usedLookupsDb.drop(writer);
    }
}
