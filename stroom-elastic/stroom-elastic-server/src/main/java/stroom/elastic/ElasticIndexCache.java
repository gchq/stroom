package stroom.elastic;

import stroom.docref.DocRef;

public interface ElasticIndexCache {
    ElasticIndexDocRefEntity get(DocRef key);

    void remove(DocRef key);
}
