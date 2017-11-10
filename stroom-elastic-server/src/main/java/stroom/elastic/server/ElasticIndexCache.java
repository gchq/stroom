package stroom.elastic.server;

import stroom.cache.CacheBean;
import stroom.elastic.shared.ElasticIndex;
import stroom.query.api.v2.DocRef;

public interface ElasticIndexCache extends CacheBean<DocRef, ElasticIndexConfig> {
    ElasticIndexConfig getOrCreate(DocRef key);
}
