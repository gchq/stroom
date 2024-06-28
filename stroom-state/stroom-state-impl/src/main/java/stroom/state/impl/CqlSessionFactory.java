package stroom.state.impl;

import stroom.docref.DocRef;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Provider;

public interface CqlSessionFactory {

    CqlSession getSession(DocRef scyllaDbDocRef);

    Provider<CqlSession> getSessionProvider(DocRef scyllaDbDocRef);
}
