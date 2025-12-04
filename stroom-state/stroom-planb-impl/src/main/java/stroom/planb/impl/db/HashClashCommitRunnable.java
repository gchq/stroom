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

import stroom.planb.impl.serde.hash.HashClashCount;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class HashClashCommitRunnable implements Consumer<Txn<ByteBuffer>>, HashClashCount {

    private int hashClashes;
    private Consumer<Txn<ByteBuffer>> runnable = txn -> {
    };

    public void setHashClashes(final int hashClashes) {
        this.hashClashes = hashClashes;
    }

    public void setRunnable(final Consumer<Txn<ByteBuffer>> runnable) {
        this.runnable = runnable;
    }

    public int getHashClashes() {
        return hashClashes;
    }

    @Override
    public void increment() {
        // We must have had a hash clash here because we didn't find a row for the key even
        // though the db contains the key hash.
        hashClashes++;
    }

    @Override
    public void accept(final Txn<ByteBuffer> txn) {
        runnable.accept(txn);
    }
}
