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

package stroom.docstore.impl.fs;

import stroom.docstore.api.RWLockFactory;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

final class StripedLockFactory implements RWLockFactory {

    private final StripedLock stripedLock = new StripedLock();

    @Override
    public void lock(final String uuid, final Runnable runnable) {
        final Lock lock = stripedLock.getLockForKey(uuid);
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T lockResult(final String uuid, final Supplier<T> supplier) {
        final Lock lock = stripedLock.getLockForKey(uuid);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
}
