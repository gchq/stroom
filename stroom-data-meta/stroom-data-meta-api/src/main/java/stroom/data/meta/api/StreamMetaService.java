package stroom.data.meta.api;

import stroom.entity.shared.BaseResultList;

import java.util.List;
import java.util.Set;

public interface StreamMetaService {
    /**
     * Create a stream with the supplied stream properties.
     *
     * @param streamProperties The properties that the newly created stream will have.
     * @return A new locked stream ready to associate written data with.
     */
    Stream createStream(StreamProperties streamProperties);

    /**
     * Get a stream from the stream meta service by id.
     *
     * @param streamId The id of the stream to retrieve.
     * @return An unlocked stream for the supplied id or null if no unlocked stream can be found.
     */
    Stream getStream(long streamId);

    /**
     * Get a stream from the stream meta service by id.
     *
     * @param streamId  The id of the stream to retrieve.
     * @param anyStatus Whether to allow locked or deleted streams to be returned.
     * @return An unlocked stream for the supplied id or null if no unlocked stream can be found unless anyStatus is true.
     */
    Stream getStream(long streamId, boolean anyStatus);

    /**
     * Change the status of the specified stream.
     *
     * @param stream       The stream to change the status for.
     * @param streamStatus The new status.
     * @return The updated stream.
     */
    Stream updateStatus(Stream stream, StreamStatus streamStatus);

    /**
     * Change the status of streams that match the supplied criteria.
     *
     * @param criteria The criteria to match streams with.
     * @return The number of streams that are updated.
     */
    int updateStatus(FindStreamCriteria criteria, StreamStatus streamStatus);

    /**
     * Add some additional attributes to a stream.
     *
     * @param stream     The stream to add attributes to.
     * @param attributes A map of key/value attributes.
     */
    void addAttributes(Stream stream, AttributeMap attributes);

    /**
     * Delete a stream by id. Note that this method will only delete unlocked streams.
     * Note that this method only changes the status of a stream to be deleted and does not actually delete the stream.
     *
     * @param streamId The id of the stream to delete.
     * @return The number of items deleted.
     */
    int deleteStream(long streamId);

    /**
     * Delete a stream by id with an option to delete regardless of lock status.
     * Note that this method only changes the status of a stream to be deleted and does not actually delete the stream.
     *
     * @param streamId  The id of the stream to delete.
     * @param lockCheck Choose if the service should only delete unlocked streams.
     * @return The number of items deleted.
     */
    int deleteStream(long streamId, boolean lockCheck);

    /**
     * Find out how many streams are locked (used in tests).
     *
     * @return A count of the number of locked streams.
     */
    int getLockCount();

    /**
     * Get a list of all unique feed names used by streams.
     *
     * @return A list of all unique feed names used by streams.
     */
    List<String> getFeeds();

    /**
     * Get a list of all unique stream type names used by streams.
     *
     * @return A list of all unique stream type names used by streams.
     */
    List<String> getStreamTypes();

    /**
     * Find streams that match the specified criteria.
     *
     * @param criteria The criteria to find matching streams with.
     * @return A list of matching streams.
     */
    BaseResultList<Stream> find(FindStreamCriteria criteria);

    /**
     * Find streams and stream attributes that match the specified criteria.
     *
     * @param criteria The criteria to find matching streams with.
     * @return A list of matching streams that includes stream attributes.
     */
    BaseResultList<StreamDataRow> findRows(FindStreamCriteria criteria);

    /**
     * Find streams and stream attributes that are related to the supplied stream.
     *
     * @param criteria The criteria to find matching streams with.
     * @return A list of matching streams that includes stream attributes.
     */
    List<StreamDataRow> findRelatedData(long streamId, boolean anyStatus);

    /**
     * Return back a aet of streams that are effective for a period in
     * question. This API is only really applicable for reference data searches.
     *
     * @param criteria the search criteria
     * @return the list of matches
     */
    Set<Stream> findEffectiveStream(EffectiveMetaDataCriteria criteria);
}
