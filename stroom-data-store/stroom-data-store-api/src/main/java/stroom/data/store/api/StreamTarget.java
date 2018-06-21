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

package stroom.data.store.api;

import stroom.data.meta.api.AttributeMap;
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
     * @return the stream associated with this target
     */
    Stream getStream();

    /**
     * Any meta data attributes associated with the data.
     */
    AttributeMap getAttributes();

    OutputStreamProvider getOutputStreamProvider();

//    /**
//     * @return the parent stream target for this child.
//     */
//    StreamTarget getParent();
}
