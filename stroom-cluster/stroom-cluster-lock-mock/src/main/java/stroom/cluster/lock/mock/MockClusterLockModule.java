package stroom.cluster.lock.mock;

import com.google.inject.AbstractModule;
import stroom.cluster.lock.api.ClusterLockService;

public class MockClusterLockModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ClusterLockService.class).to(MockClusterLockService.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
