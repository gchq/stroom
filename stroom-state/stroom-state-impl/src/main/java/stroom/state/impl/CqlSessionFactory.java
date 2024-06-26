package stroom.state.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Provider;

public interface CqlSessionFactory {

    CqlSession getSession(String keyspace);

    Provider<CqlSession> getSessionProvider(String keyspace);
}
