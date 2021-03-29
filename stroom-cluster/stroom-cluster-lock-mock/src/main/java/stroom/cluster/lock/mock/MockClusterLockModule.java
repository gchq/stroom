package stroom.cluster.lock.mock;

import stroom.cluster.lock.api.ClusterLockService;

import com.google.inject.AbstractModule;

public class MockClusterLockModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClusterLockService.class).to(MockClusterLockService.class);
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
