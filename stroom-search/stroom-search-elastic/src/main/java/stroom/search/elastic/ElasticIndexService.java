package stroom.search.elastic;

import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.DocRef;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;

import java.util.List;
import java.util.Map;

public interface ElasticIndexService {
    List<DataSourceField> getDataSourceFields(ElasticIndex index);

    List<ElasticIndexField> getFields(ElasticIndex index);

    Map<String, ElasticIndexField> getFieldsMap(ElasticIndex index);

    List<String> getStoredFields(ElasticIndex index);
}
