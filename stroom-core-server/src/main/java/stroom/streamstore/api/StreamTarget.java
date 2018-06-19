/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.api;

import stroom.feed.MetaMap;
import stroom.data.meta.api.Stream;

import java.io.Closeable;
import java.io.OutputStream;

/**
 * <p>
 * Interface that represents a stream target. Make sure you close the stream
 * once finished (as this unlocks the file).
 * </p>
 */
public interface StreamTarget extends Closeable {
    /**
     * @return a type associated with the stream. Used to differentiate between
     * child streams ("ctx", "idx", etc).
     */
    String getStreamTypeName();

    /**
     * @return the stream associated with this target
     */
    Stream getStream();

    /**
     * Any attributes regarding the stream
     */
    MetaMap getAttributeMap();

    /**
     * @return the real IO output stream
     */
    OutputStream getOutputStream();

    /**
     * Add a child stream for this main stream.
     *
     * @param streamTypeName name of the child.
     * @return target to write to.
     */
    StreamTarget addChildStream(String streamTypeName);

    /**
     * Get a child stream (null if addChildStream has not been called)
     *
     * @param streamTypeName
     * @return
     */
    StreamTarget getChildStream(String streamTypeName);

    /**
     * @return the parent stream for this child.
     */
    StreamTarget getParent();

    /**
     * @return are we appending?
     */
    boolean isAppend();
}
