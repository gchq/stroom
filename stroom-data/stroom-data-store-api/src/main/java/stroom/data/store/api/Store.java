/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.data.store.api;

import stroom.meta.api.MetaProperties;

/**
 * <p>
 * API to the data store.
 * </p>
 * <p>
 * <p>
 * The store abstracts a repository of large files keyed by some meta
 * data.
 * </p>
 * <p>
 * <p>
 * When you read or write to a stream the file is locked. You must close the
 * stream to unlock the file.
 * </p>
 */
public interface Store {

    Target openTarget(MetaProperties metaProperties) throws DataException;

    /**
     * <p>
     * Open a new target (i.e. new file) based on some meta data
     * </p>
     *
     * @return the stream to write to
     */
    Target openTarget(MetaProperties metaProperties, String volumeGroup) throws DataException;

    /**
     * <p>
     * Delete a open stream.
     * </p>
     *
     * @return items deleted
     */
    void deleteTarget(Target target);

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId the id of the stream to open.
     * @return The stream source if the stream can be found.
     * @throws DataException in case of a IO error or stream volume not visible or non
     *                       existent.
     */
    Source openSource(long streamId) throws DataException;

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
     * @throws DataException Could be thrown if no volume
     */
    Source openSource(long streamId, boolean anyStatus) throws DataException;
}
