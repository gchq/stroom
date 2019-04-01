package stroom.index.impl;

import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;

import java.util.List;

public interface IndexShardDao {
    /**
     * Retrieve a specific Shard by it's ID
     * @param id The Database ID of the shard
     * @return The Shard from the database
     */
    IndexShard loadById(Long id);

    /**
     * Locate shards based on various criteria
     * @param criteria The details of the query
     * @return Index Shards matching the criteria
     */
    List<IndexShard> find(FindIndexShardCriteria criteria);

    /**
     * Create a new Index Shard
     * @param key The Index Shard details
     * @param volumeGroupName The volume to use
     * @param ownerNodeName The node that will own the shard
     * @param indexVersion The version of the index in use
     * @return The newly created Index Shard
     */
    IndexShard create(IndexShardKey key, String volumeGroupName, String ownerNodeName, String indexVersion);

    /**
     * Delete a specific shard, by it's ID
     * @param id The database ID of the shard to delete
     */
    void delete(Long id);

    /**
     * Update the status of a shard
     * @param id The database ID of the shard to update
     * @param status The new status value
     */
    void setStatus(Long id, IndexShard.IndexShardStatus status);

    /**
     * Update the details of the contents of a shard
     * @param id The database ID
     * @param documentCount The number of documents
     * @param commitDurationMs commitDurationMs
     * @param commitMs commitMs
     * @param fileSize fileSize
     */
    void update(Long id, Integer documentCount, Long commitDurationMs, Long commitMs, Long fileSize);
}
