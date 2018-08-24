package stroom.elastic.impl;

import stroom.docref.DocRef;

public interface ElasticIndexConfigCache {
    ElasticIndexConfigDoc get(DocRef key);

    void remove(DocRef key);
}
