package stroom.elastic.api;

import stroom.docref.DocRef;

import java.util.Optional;

public interface ElasticIndexWriterFactory {
    Optional<ElasticIndexWriter> create(DocRef elasticConfigRef);
}
