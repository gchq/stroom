package stroom.elastic.server;

import stroom.query.api.v2.DocRef;

public interface ElasticIndexCache {
    ElasticIndexDocRefEntity get(DocRef key);

    void remove(DocRef key);
}
