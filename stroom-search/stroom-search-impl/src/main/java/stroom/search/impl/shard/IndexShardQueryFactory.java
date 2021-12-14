package stroom.search.impl.shard;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

interface IndexShardQueryFactory {

    Query getQuery(Version luceneVersion);
}
