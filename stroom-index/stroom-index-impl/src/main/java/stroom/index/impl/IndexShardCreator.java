package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneVersion;

public interface IndexShardCreator {

    /**
     * Create a new Lucene index shard on the file system and record it in the DB.
     *
     * @param indexShardKey Info about the index and partition to create the shard for.
     * @param ownerNodeName The node that will own the shard.
     * @return The newly created index shard.
     */

    IndexShard createIndexShard(IndexShardKey indexShardKey, String ownerNodeName);

    /**
     * Set the Lucene version we want to create index shards for.
     *
     * @param indexVersion
     */
    void setIndexVersion(LuceneVersion indexVersion);
}
