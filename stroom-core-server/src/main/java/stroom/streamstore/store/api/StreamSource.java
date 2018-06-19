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

package stroom.streamstore.store.api;

import stroom.feed.MetaMap;
import stroom.data.meta.api.Stream;

import java.io.Closeable;
import java.io.InputStream;

/**
 * <p>
 * Interface that represents a stream source. Make sure you close the stream
 * once finished (as this unlocks the file).
 * </p>
 */
public interface StreamSource extends Closeable {
    /**
     * @return a type associated with the stream. Used to differentiate between
     * child streams ("ctx", "idx", etc).
     */
    String getStreamTypeName();

    /**
     * @return the stream associated with this source
     */
    Stream getStream();

    /**
     * @return the real IO input stream
     */
    InputStream getInputStream();

    /**
     * Any attributes regarding the stream
     */
    MetaMap getAttributeMap();

    /**
     * Depending on the type of stream we we may return back null if the stream
     * does not exist. Some streams a null file means empty where as others mean
     * not stream. The STORE_LAZY map in FileSystem util governs this.
     *
     * @param type to get
     * @return back the child stream based on type
     */
    StreamSource getChildStream(String streamTypeName);

    /**
     * @return the parent or null
     */
    StreamSource getParent();
}
