/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;

import java.io.Closeable;
import java.io.IOException;

/**
 * <p>
 * Interface that represents a stream source. Make sure you close the stream
 * once finished (as this unlocks the file).
 * </p>
 */
public interface Source extends Closeable {

    /**
     * Get the meta data associated with this source.
     *
     * @return the meta data associated with this source
     */
    Meta getMeta();

    /**
     * Any meta data attributes associated with the data.
     *
     * @return meta data attributes associated with the data.
     */
    AttributeMap getAttributes();

    /**
     * Get an input stream provider for the nested data item (i.e. part) specified by the provided 0 based index.
     *
     * @param index The zero based index of the nested data item (i.e. part) to get an input stream provider for.
     * @return An input stream provider for the specified index or throw an IOException if the index is out of bounds.
     */
    InputStreamProvider get(long index) throws IOException;

    /**
     * How many nested data items (i.e. parts) does this source contain.
     */
    long count() throws IOException;

    /**
     * How many nested data items (i.e. parts) does this child stream source contain.
     */
    long count(final String childStreamType) throws IOException;
}
