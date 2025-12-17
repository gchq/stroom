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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A striped lock that uses hashCode on the key to give back hopefully non-used
 * lock.
 */
final class StripedLock {

    /**
     * We use a number here based on our typical concurrency profile. So 10
     * indexing threads and 2048 locks seems OK.
     */
    private static final int DEFAULT_NUMBER_OF_MUTEXES = 2048;
    private static final int DOUG_LEA_BLACK_MAGIC_OPERAND_1 = 20;
    private static final int DOUG_LEA_BLACK_MAGIC_OPERAND_2 = 12;
    private static final int DOUG_LEA_BLACK_MAGIC_OPERAND_3 = 7;
    private static final int DOUG_LEA_BLACK_MAGIC_OPERAND_4 = 4;
    private Lock[] mutexes = null;

    StripedLock() {
        mutexes = new Lock[DEFAULT_NUMBER_OF_MUTEXES];

        for (int i = 0; i < mutexes.length; i++) {
            mutexes[i] = new ReentrantLock();
        }
    }

    Lock getLockForKey(final Object key) {
        final int lockNumber = selectLock(key, DEFAULT_NUMBER_OF_MUTEXES);
        return mutexes[lockNumber];
    }

    /**
     * Returns a hash code for non-null Object x.
     * <p/>
     * This function ensures that hashCodes that differ only by constant
     * multiples at each bit position have a bounded number of collisions. (Doug
     * Lea)
     */
    private int hash(final Object object) {
        int h = object.hashCode();
        h ^= (h >>> DOUG_LEA_BLACK_MAGIC_OPERAND_1) ^ (h >>> DOUG_LEA_BLACK_MAGIC_OPERAND_2);
        return h ^ (h >>> DOUG_LEA_BLACK_MAGIC_OPERAND_3) ^ (h >>> DOUG_LEA_BLACK_MAGIC_OPERAND_4);
    }

    /**
     * Selects a lock for a key. The same lock is always used for a given key.
     */
    private int selectLock(final Object key, final int numberOfLocks) {
        final int number = numberOfLocks & (numberOfLocks - 1);
        if (number != 0) {
            throw new RuntimeException("Lock number must be a power of two: " + numberOfLocks);
        }
        if (key == null) {
            return 0;
        } else {
            final int hash = hash(key) & (numberOfLocks - 1);
            return hash;
        }
    }
}
