/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.data.store.api;

import stroom.data.meta.api.AttributeMap;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataProperties;

/**
 * <p>
 * API to the stream store.
 * </p>
 * <p>
 * <p>
 * The stream store abstracts a repository of large files keyed by some meta
 * data.
 * </p>
 * <p>
 * <p>
 * When you read or write to a stream the file is locked. You must close the
 * stream to unlock the file.
 * </p>
 */
public interface StreamStore {
    /**
     * <p>
     * Open a new stream (i.e. new file) based on some meta data
     * </p>
     *
     * @return the stream to write to
     */
    StreamTarget openStreamTarget(DataProperties streamProperties) throws StreamException;

    /**
     * <p>
     * Open a new stream (i.e. new file) based on some meta data
     * </p>
     *
     * @param append allow appending to the stream (or wipe it?)
     * @return the stream to write to
     */
    StreamTarget openExistingStreamTarget(Data stream) throws StreamException;

    /**
     * <p>
     * Close a open stream target so it can be read by someone else.
     * </p>
     */
    void closeStreamTarget(StreamTarget streamTarget);

    /**
     * <p>
     * Delete a open stream.
     * </p>
     *
     * @return items deleted
     */
    int deleteStreamTarget(StreamTarget target);

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId the id of the stream to open.
     * @return The stream source if the stream can be found.
     * @throws StreamException in case of a IO error or stream volume not visible or non
     *                         existent.
     */
    StreamSource openStreamSource(long streamId) throws StreamException;

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId  The stream id to open a stream source for.
     * @param anyStatus Used to specify if this method will return stream sources that
     *                  are logically deleted or locked. If false only unlocked stream
     *                  sources will be returned, null otherwise.
     * @return The loaded stream source if it exists (has not been physically
     * deleted) else null. Also returns null if one exists but is
     * logically deleted or locked unless <code>anyStatus</code> is
     * true.
     * @throws StreamException Could be thrown if no volume
     */
    StreamSource openStreamSource(long streamId, boolean anyStatus) throws StreamException;


    /**
     * <p>
     * Close a open stream source so it can be read by someone else.
     * </p>
     */
    void closeStreamSource(StreamSource streamSource);

    /**
     * Gets the meta data that was stored in the stream store against the supplied stream id.
     *
     * @param streamId
     * @return
     */
    AttributeMap getStoredMeta(Data stream);
}
