package stroom.state.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class CqlSessionFactoryImpl implements CqlSessionFactory {

    private final CqlSessionCache cqlSessionCache;

    @Inject
    public CqlSessionFactoryImpl(final CqlSessionCache cqlSessionCache) {
        this.cqlSessionCache = cqlSessionCache;
    }

    @Override
    public CqlSession getSession(final String keyspace) {
        return cqlSessionCache.get(keyspace);
    }

    @Override
    public Provider<CqlSession> getSessionProvider(final String keyspace) {
        return () -> getSession(keyspace);
    }
}
