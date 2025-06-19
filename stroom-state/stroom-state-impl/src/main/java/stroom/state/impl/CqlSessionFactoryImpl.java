package stroom.state.impl;

import stroom.docref.DocRef;

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
    public CqlSession getSession(final DocRef scyllaDbDocRef) {
        return cqlSessionCache.get(scyllaDbDocRef);
    }

    @Override
    public Provider<CqlSession> getSessionProvider(final DocRef scyllaDbDocRef) {
        return () -> getSession(scyllaDbDocRef);
    }
}
