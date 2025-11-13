/*
 * Copyright 2016 Crown Copyright
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

package stroom.cluster.lock.impl.db;

import stroom.cluster.lock.api.ClusterLockService;

import jakarta.inject.Inject;

import java.util.function.Supplier;

class ClusterLockServiceImpl implements ClusterLockService {

    private final DbClusterLock dbClusterLock;

    @Inject
    ClusterLockServiceImpl(final DbClusterLock dbClusterLock) {
        this.dbClusterLock = dbClusterLock;
    }

    @Override
    public void lock(final String lockName, final Runnable runnable) {
        dbClusterLock.lockResult(lockName, () -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <T> T lockResult(final String lockName, final Supplier<T> supplier) {
        return dbClusterLock.lockResult(lockName, supplier);
    }

    @Override
    public void tryLock(final String lockName, final Runnable runnable) {
        dbClusterLock.tryLock(lockName, runnable);
    }
}
