package stroom.streamstore.meta.api;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;

import java.util.List;

public interface StreamMetaService {
    Stream createStream(StreamProperties streamProperties);

    /**
     * Get a stream from the stream meta service by id.
     *
     * @param id
     * @return
     */
    Stream getStream(long streamId);

    /**
     * Get a stream from the stream meta service by id.
     *
     * @param id
     * @return
     */
    Stream getStream(long streamId, boolean anyStatus);

    /**
     * Check if the current user is allowed to read the stream specified by id.
     *
     * @param id
     * @return
     */
    boolean canReadStream(long streamId);


    Stream updateStatus(long id, StreamStatus streamStatus);

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
