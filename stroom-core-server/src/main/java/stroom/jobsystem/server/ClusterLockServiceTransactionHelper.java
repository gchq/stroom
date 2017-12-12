package stroom.jobsystem.server;

import stroom.entity.shared.Clearable;

public interface ClusterLockServiceTransactionHelper extends Clearable {

    void checkLockCreated(String name);
}
