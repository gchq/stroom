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

public class HashLookupRecorder implements UsedLookupsRecorder {

    private final UsedLookupsDb usedLookupsDb;
    private final HashLookupDb hashLookupDb;

    public HashLookupRecorder(final PlanBEnv env,
                              final HashLookupDb hashLookupDb) {
        this.usedLookupsDb = new UsedLookupsDb(env, hashLookupDb.getName() + "-HashLookupDb");
        this.hashLookupDb = hashLookupDb;
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        usedLookupsDb.record(writer, byteBuffer);
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        hashLookupDb.forEachHash(readTxn, hash -> {
            if (usedLookupsDb.isUnused(writer.getWriteTxn(), hash)) {
                hashLookupDb.deleteByHash(writer.getWriteTxn(), hash);
                writer.tryCommit();
            }
        });
        usedLookupsDb.drop(writer);
    }
}
