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
