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

package stroom.pipeline.destination;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public abstract class RollingDestination implements Destination {
    private final ReentrantLock lock = new ReentrantLock();

    void lock() {
        lock.lock();
    }

    boolean tryLock() {
        return lock.tryLock();
    }

    void unlock() {
        lock.unlock();
    }

    abstract Object getKey();

    /**
     * Try to flush this destination if it needs to and roll it if it needs to.
     * If this destination is rolled then this method will return true.
     *
     * @return True if this destination has been rolled.
     */
    abstract boolean tryFlushAndRoll(boolean force, long currentTime) throws IOException;
}
