package stroom.streamstore.meta.api;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;

import java.util.List;
import java.util.Map;

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
     * Check if the current user is allowed to read the stream specified by id.
     *
     * @param streamId The id of the stream to check.
     * @return True if the current user is allowed to read the stream.
     */
    boolean canReadStream(long streamId);

    Stream updateStatus(long streamId, StreamStatus streamStatus);

    void addAttributes(Stream stream, Map<String, String> attributes);

    /**
     * <p>
     * Delete a stream.
     * </p>
     *
     * @return items deleted
     */
    int deleteStream(long streamId);

    int deleteStream(long streamId, boolean lockCheck);

    int getLockCount();

    /**
     * Return the total period of streams in the stream store
     */
    Period getCreatePeriod();

    List<String> getFeeds();

    List<String> getStreamTypes();

    BaseResultList<Stream> find(FindStreamCriteria criteria);

    BaseResultList<StreamDataRow> findRows(FindStreamCriteria criteria);

    int findDelete(FindStreamCriteria criteria);

    /**
     * <p>
     * Return back a list of streams that are effective for a period in
     * question. This API is only really applicable for reference data searches.
     * </p>
     *
     * @param criteria the search criteria
     * @return the list of matches
     */
    List<Stream> findEffectiveStream(EffectiveMetaDataCriteria criteria);
}
