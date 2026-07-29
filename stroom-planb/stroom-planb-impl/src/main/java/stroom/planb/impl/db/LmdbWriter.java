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

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class LmdbWriter implements AutoCloseable {

    private final Env<ByteBuffer> env;
    private final ReentrantLock dbCommitLock;
    private final Consumer<Txn<ByteBuffer>> commitListener;
    private final ReentrantLock writeTxnLock;
    private Txn<ByteBuffer> writeTxn;
    private int changeCount = 0;
    private boolean aborted;

    public LmdbWriter(final Env<ByteBuffer> env,
                      final ReentrantLock dbCommitLock,
                      final Consumer<Txn<ByteBuffer>> commitListener,
                      final ReentrantLock writeTxnLock) {
        this.env = env;
        this.dbCommitLock = dbCommitLock;
        this.commitListener = commitListener;
        this.writeTxnLock = writeTxnLock;

        // We are only allowed a single write txn and we can only write with a single thread so ensure this is the
        // case.
        writeTxnLock.lock();
    }

    /**
     * The current write txn, opened on first request.
     *
     * <p>Do not hold the returned txn across a {@link #tryCommit()}, {@link #commit()} or
     * {@link #abort()} — those close it, and a later use of the stale reference fails with
     * {@code Txn$NotReadyException}. A loop that commits per item must therefore call this again on
     * each iteration rather than hoisting it.</p>
     */
    public Txn<ByteBuffer> getWriteTxn() {
        if (aborted) {
            throw new IllegalStateException(
                    "This writer was aborted after a failed write and cannot accept more work");
        }
        if (writeTxn == null) {
            writeTxn = env.txnWrite();
        }
        return writeTxn;
    }

    public void tryCommit() {
        incrementChangeCount();
        if (shouldCommit()) {
            commit();
        }
    }

    public void incrementChangeCount() {
        changeCount++;
    }

    public boolean shouldCommit() {
        return changeCount > 10000;
    }

    /**
     * Discards the current txn without running the commit listener. Once a native LMDB operation
     * has failed, e.g. with MDB_MAP_FULL, the txn is flagged MDB_TXN_ERROR and every subsequent
     * use of it fails with MDB_BAD_TXN. Committing such a txn therefore throws from the listener's
     * own put and masks the original cause, so callers must abort instead.
     */
    public void abort() {
        dbCommitLock.lock();
        try {
            aborted = true;
            if (writeTxn != null) {
                try {
                    writeTxn.close();
                } finally {
                    writeTxn = null;
                }
            }

            changeCount = 0;
        } finally {
            dbCommitLock.unlock();
        }
    }

    public void commit() {
        dbCommitLock.lock();
        try {
            if (writeTxn != null) {
                try {
                    commitListener.accept(writeTxn);
                    writeTxn.commit();
                } finally {
                    try {
                        writeTxn.close();
                    } finally {
                        writeTxn = null;
                    }
                }
            }

            changeCount = 0;
        } finally {
            dbCommitLock.unlock();
        }
    }

    @Override
    public void close() {
        try {
            if (aborted) {
                abort();
            } else {
                commit();
            }
        } finally {
            writeTxnLock.unlock();
        }
    }
}
