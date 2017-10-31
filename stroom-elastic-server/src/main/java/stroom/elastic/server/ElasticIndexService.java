package stroom.elastic.server;

import stroom.entity.server.DocumentEntityService;
import stroom.entity.server.FindService;

public interface ElasticIndexService
        extends DocumentEntityService<ElasticIndex>, FindService<ElasticIndex, FindElasticIndexCriteria> {
}
