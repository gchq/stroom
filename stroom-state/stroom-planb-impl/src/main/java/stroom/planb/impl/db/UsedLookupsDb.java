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

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class UsedLookupsDb {

    private static final ByteBuffer VALUE = ByteBuffer.allocateDirect(0);
    private final Dbi<ByteBuffer> dbi;

    public UsedLookupsDb(final PlanBEnv env,
                         final String name) {
        dbi = env.openDbi(name + "-used", DbiFlags.MDB_CREATE);
    }

    public void record(final LmdbWriter writer, final ByteBuffer key) {
        dbi.put(writer.getWriteTxn(), key, VALUE);
    }

    public boolean isUnused(final Txn<ByteBuffer> readTxn, final ByteBuffer key) {
        return dbi.get(readTxn, key) == null;
    }

    public void drop(final LmdbWriter writer) {
        dbi.drop(writer.getWriteTxn());
    }
}
