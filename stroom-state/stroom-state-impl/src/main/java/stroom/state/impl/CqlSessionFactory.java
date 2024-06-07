package stroom.state.impl;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;

import java.util.Objects;

public class CqlSessionFactory {

    private final CqlSessionCache cqlSessionCache;
    private final ScyllaDbDocCache scyllaDbDocCache;
    private final StateDocCache stateDocCache;

    @Inject
    public CqlSessionFactory(final CqlSessionCache cqlSessionCache,
                             final ScyllaDbDocCache scyllaDbDocCache,
                             final StateDocCache stateDocCache) {
        this.cqlSessionCache = cqlSessionCache;
        this.scyllaDbDocCache = scyllaDbDocCache;
        this.stateDocCache = stateDocCache;
    }

    public CqlSession getSession(final DocRef stateDocRef) {
        Objects.requireNonNull(stateDocRef, "Null state doc ref");
        final StateDoc stateDoc = stateDocCache.get(stateDocRef);
        return getSession(stateDoc);
    }

    public CqlSession getSession(final StateDoc stateDoc) {
        Objects.requireNonNull(stateDoc, "Null state doc");
        Objects.requireNonNull(stateDoc.getScyllaDbRef(), "Null ScyllaDB ref");

        final ScyllaDbDoc scyllaDbDoc = scyllaDbDocCache.get(stateDoc.getScyllaDbRef());
        Objects.requireNonNull(scyllaDbDoc, "Unable to find ScyllaDB doc referenced by " +
                DocRefUtil.createSimpleDocRefString(stateDoc.getScyllaDbRef()));

        return cqlSessionCache.get(scyllaDbDoc);
    }
}
