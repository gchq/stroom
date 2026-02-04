/*
 * Copyright 2018 Crown Copyright
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

package stroom.lmdb.serde;

import java.nio.ByteBuffer;

public interface Deserializer<T> {

    /**
     * Read object T from byteBuffer, advancing the buffer and flipping it once the object has been read.
     *
     * @return The de-serialised object
     */
    T deserialize(final ByteBuffer byteBuffer);

    // TODO we could do with a get(byteBuffer) method that reads from the buffer, advancing it,
    //  but not flipping it, e.g. when you are de-serialising part of the buffer. However, that means
    //  adding it to lots of impls.
}
