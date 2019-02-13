package stroom.cluster.lock.impl.db;

import stroom.entity.shared.Clearable;

public interface ClusterLockServiceTransactionHelper extends Clearable {
    void checkLockCreated(String name);
}