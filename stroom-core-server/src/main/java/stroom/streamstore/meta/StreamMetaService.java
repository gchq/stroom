package stroom.streamstore.meta;

import stroom.entity.FindDeleteService;
import stroom.entity.FindService;
import stroom.entity.shared.Period;
import stroom.streamstore.EffectiveMetaDataCriteria;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;

import java.util.List;

public interface StreamMetaService extends FindService<Stream, FindStreamCriteria>, FindDeleteService<FindStreamCriteria> {
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


    Stream createStream(StreamProperties streamProperties);

    /**
     * Check if the current user is allowed to read the stream specified by id.
     *
     * @param id
     * @return
     */
    boolean canReadStream(long streamId);

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


    Stream updateStatus(long id, StreamStatus streamStatus);

    /**
     * <p>
     * Delete a stream.
     * </p>
     *
     * @return items deleted
     */
    Long deleteStream(long streamId);

    Long deleteStream(long streamId, boolean lockCheck);

    long getLockCount();

    /**
     * Return the total period of streams in the stream store
     */
    Period getCreatePeriod();

    List<String> getFeeds();

    List<String> getStreamTypes();
}
