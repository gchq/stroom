package stroom.search.elastic;


import stroom.query.api.v2.DocRef;
import stroom.search.elastic.shared.ElasticIndexField;

import java.util.Map;

public interface ElasticIndexService {
    Map<String, ElasticIndexField> getFieldsMap(DocRef docRef);
}
