/*
 * Copyright 2026 Crown Copyright
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


package stroom.index.lucene;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.Lock;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/// A directory whose write lock is only valid while the lease on its index shard is held.
///
/// Lucene checks that its write lock is valid before it creates, deletes, renames or syncs any file. With
/// this directory a writer that has lost its lease can therefore no longer change the files of its shard,
/// whatever it is asked to do. That matters because the shard may by then have been taken by another node,
/// and a writer that rolls back deletes every file in the directory that it does not know about, which
/// would include all that the other node has written.
class LeaseCheckedDirectory extends FilterDirectory {

    private final BooleanSupplier leaseHeld;

    /// @param directory The directory to wrap.
    /// @param leaseHeld Says whether the lease is still held.
    LeaseCheckedDirectory(final Directory directory, final BooleanSupplier leaseHeld) {
        super(directory);
        this.leaseHeld = Objects.requireNonNull(leaseHeld);
    }

    @Override
    public Lock obtainLock(final String name) throws IOException {
        return new LeaseCheckedLock(in.obtainLock(name), leaseHeld);
    }

    private static class LeaseCheckedLock extends Lock {

        private final Lock lock;
        private final BooleanSupplier leaseHeld;

        private LeaseCheckedLock(final Lock lock, final BooleanSupplier leaseHeld) {
            this.lock = lock;
            this.leaseHeld = leaseHeld;
        }

        @Override
        public void close() throws IOException {
            lock.close();
        }

        @Override
        public void ensureValid() throws IOException {
            lock.ensureValid();
            if (!leaseHeld.getAsBoolean()) {
                throw new IOException("The lease on the index shard is no longer held, so its files must " +
                                      "not be changed: " + lock);
            }
        }

        @Override
        public String toString() {
            return lock.toString();
        }
    }
}
