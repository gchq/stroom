package stroom.streamstore.meta.db;

import stroom.entity.FindDeleteService;
import stroom.entity.FindService;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.streamstore.EffectiveMetaDataCriteria;
import stroom.streamstore.OldFindStreamCriteria;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamEntity;

import java.util.List;

public interface StreamEntityService {
//    StreamEntity createStream(StreamProperties streamProperties);
//
//    /**
//     * Load a stream by id.
//     *
//     * @param id The stream id to load a stream for.
//     * @return The loaded stream if it exists (has not been physically deleted)
//     * and is not logically deleted or locked, null otherwise.
//     */
//    StreamEntity loadStreamById(long id);
//
//    /**
//     * Load a stream by id.
//     *
//     * @param id        The stream id to load a stream for.
//     * @param anyStatus Used to specify if this method will return streams that are
//     *                  logically deleted or locked. If false only unlocked streams
//     *                  will be returned, null otherwise.
//     * @return The loaded stream if it exists (has not been physically deleted)
//     * else null. Also returns null if one exists but is logically
//     * deleted or locked unless <code>anyStatus</code> is true.
//     */
//    StreamEntity loadStreamById(long id, boolean anyStatus);
//
//    StreamEntity save(StreamEntity stream);
//
//    /**
//     * <p>
//     * Open a existing stream source.
//     * </p>
//     *
//     * @param streamId the id of the stream to open.
//     * @return The stream source if the stream can be found.
//     * @throws StreamException in case of a IO error or stream volume not visible or non
//     *                         existent.
//     */
//    StreamSource openStreamSource(long streamId) throws StreamException;
//
//    /**
//     * <p>
//     * Open a existing stream source.
//     * </p>
//     *
//     * @param streamId  The stream id to open a stream source for.
//     * @param anyStatus Used to specify if this method will return stream sources that
//     *                  are logically deleted or locked. If false only unlocked stream
//     *                  sources will be returned, null otherwise.
//     * @return The loaded stream source if it exists (has not been physically
//     * deleted) else null. Also returns null if one exists but is
//     * logically deleted or locked unless <code>anyStatus</code> is
//     * true.
//     * @throws StreamException Could be thrown if no volume
//     */
//    StreamSource openStreamSource(long streamId, boolean anyStatus) throws StreamException;
//
//    /**
//     * <p>
//     * Open a new stream (i.e. new file) based on some meta data
//     * </p>
//     *
//     * @return the stream to write to
//     */
//    StreamTarget openStreamTarget(StreamProperties streamProperties) throws StreamException;
//
//    /**
//     * <p>
//     * Open a new stream (i.e. new file) based on some meta data
//     * </p>
//     *
//     * @param append allow appending to the stream (or wipe it?)
//     * @return the stream to write to
//     */
//    StreamTarget openExistingStreamTarget(long streamId) throws StreamException;
//
//    /**
//     * <p>
//     * Close a open stream source so it can be read by someone else.
//     * </p>
//     */
//    void closeStreamSource(StreamSource streamSource);
//
//    /**
//     * <p>
//     * Close a open stream target so it can be read by someone else.
//     * </p>
//     */
//    void closeStreamTarget(StreamTarget streamTarget);
//
//    /**
//     * <p>
//     * Delete a stream.
//     * </p>
//     *
//     * @return items deleted
//     */
//    Long deleteStream(long streamId);

    BaseResultList<Stream> find(OldFindStreamCriteria findStreamCriteria);

//    /**
//     * <p>
//     * Delete a open stream.
//     * </p>
//     *
//     * @return items deleted
//     */
//    Long deleteStreamTarget(StreamTarget target);

//    /**
//     * <p>
//     * Return back a list of streams that are effective for a period in
//     * question. This API is only really applicable for reference data searches.
//     * </p>
//     *
//     * @param criteria the search criteria
//     * @return the list of matches
//     */
//    List<Stream> findEffectiveStream(EffectiveMetaDataCriteria criteria);

//    /**
//     * Return the number of open locks.
//     */
//    long getLockCount();


}
