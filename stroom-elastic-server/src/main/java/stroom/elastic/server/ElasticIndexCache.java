package stroom.elastic.server;

import stroom.query.api.v2.DocRef;

public interface ElasticIndexCache {
    ElasticIndexConfig get(DocRef key);

    void remove(DocRef key);
}
