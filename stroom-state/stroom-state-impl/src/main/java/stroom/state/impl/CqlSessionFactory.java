package stroom.state.impl;

import stroom.docref.DocRef;
import stroom.state.shared.StateDoc;

import com.datastax.oss.driver.api.core.CqlSession;

public interface CqlSessionFactory {

    CqlSession getSession(DocRef stateDocRef);

    CqlSession getSession(StateDoc stateDoc);
}
