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

package stroom.data.store.impl.fs;

import java.io.IOException;
import java.io.InputStream;

/**
 * @see BlockGZIPConstants
 */
class BlockGZIPInputStream extends BlockGZIPInput {
    private final InputStream inputStream;

    /**
     * Read a BGZIP IO stream.
     */
    BlockGZIPInputStream(final InputStream inputStream, final int rawBufferSize) throws IOException {
        super(rawBufferSize);
        this.inputStream = inputStream;
        init();
    }

    /**
     * Read a BGZIP IO stream.
     */
    BlockGZIPInputStream(final InputStream inputStream) throws IOException {
        this.inputStream = inputStream;
        init();
    }

    @Override
    protected InputStream getRawStream() {
        return inputStream;
    }

    @Override
    void invalid(final String message) throws IOException {
        throw new IOException(message);
    }
}
