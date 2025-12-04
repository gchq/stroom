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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class used to keep track of how many bytes we have read from a buffered
 * stream.
 * <p>
 * Also allows us to use the buffer to read another stream.
 */
class BlockBufferedInputStream extends BufferedInputStream {

    /**
     * How far we have read along the stream.
     */
    private long streamPosition = 0;

    /**
     * @param in the stream we are buffering.
     */
    BlockBufferedInputStream(final InputStream in) {
        super(in);
    }

    /**
     * @param in      the stream we are buffering.
     * @param bufSize buffer size to use
     */
    BlockBufferedInputStream(final InputStream in, final int bufSize) {
        super(in, bufSize);
    }

    /**
     * Point at a new input stream and reset our markers. Used to avoid
     * allocating new buffers.
     *
     * @param newIn new stream to read
     */
    void recycle(final InputStream newIn) {
        count = 0;
        pos = 0;
        streamPosition = 0;
        in = newIn;
        markpos = -1;
        marklimit = 0;
    }

    @Override
    public int read() throws IOException {
        final int rtn = super.read();
        if (rtn != -1) {
            // did we read a byte?
            streamPosition++;
        }
        return rtn;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(final byte[] b) throws IOException {
        final int rtn = super.read(b);
        if (rtn != -1) {
            // Accumulate the bytes read
            streamPosition += rtn;
        }
        return rtn;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int rtn = super.read(b, off, len);
        if (rtn != -1) {
            // Accumulate the bytes read
            streamPosition += rtn;
        }
        return rtn;
    }

//    /**
//     * @return how far down the stream we are
//     */
//    public long getStreamPosition() {
//        return streamPosition;
//    }

    @Override
    public String toString() {
        return in.toString();
    }

}
