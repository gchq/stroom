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

package stroom.util.io;

import java.io.IOException;

/**
 * A more advanced input stream that knows it's size, position and how to seek
 * into the stream.
 */
public interface Seekable {

    /**
     * @return the total size of this stream.
     */
    long getSize() throws IOException;

    /**
     * @return the current position we are at in the stream.
     */
    long getPosition() throws IOException;

    /**
     * Seek to a known stop in the stream.
     */
    void seek(long pos) throws IOException;
}
