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

package stroom.pipeline.writer;

import java.io.IOException;
import java.io.OutputStream;

public interface Output {

    OutputStream getOutputStream();

    default void insertSegmentMarker() throws IOException {
    }

    default void startZipEntry() throws IOException {
    }

    default void endZipEntry() throws IOException {
    }

    default boolean isZip() {
        return false;
    }

    long getCurrentOutputSize();

    boolean getHasBytesWritten();

    void write(final byte[] bytes) throws IOException;

    void close() throws IOException;
}
