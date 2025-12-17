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

package stroom.index.lucene;

import java.util.Objects;

class ShardLockKey {

    private final String canonicalPath;
    private final String lockName;

    ShardLockKey(final String canonicalPath, final String lockName) {
        this.canonicalPath = canonicalPath;
        this.lockName = lockName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ShardLockKey lockKey = (ShardLockKey) o;
        return Objects.equals(canonicalPath, lockKey.canonicalPath) &&
                Objects.equals(lockName, lockKey.lockName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalPath, lockName);
    }

    @Override
    public String toString() {
        return "ShardLockKey{" +
                "canonicalPath='" + canonicalPath + '\'' +
                ", lockName='" + lockName + '\'' +
                '}';
    }
}
