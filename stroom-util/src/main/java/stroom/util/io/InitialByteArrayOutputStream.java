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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream buffer that tries to use a given buffer first and once that
 * is not big enough it delegates all calls to a real ByteArrayOutputStream.
 * <p>
 * It also allows access to the buffer and pos without a copy of the array.
 */
public class InitialByteArrayOutputStream extends OutputStream {

    private final byte[] preBuffer;
    private int preBufferPos = 0;
    private GetBufferByteArrayOutputStream postBuffer;

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public InitialByteArrayOutputStream(final byte[] initialBuffer) {
        preBuffer = initialBuffer;
    }

    public BufferPos getBufferPos() {
        if (postBuffer == null) {
            return new BufferPos(preBuffer, preBufferPos);
        } else {
            return new BufferPos(postBuffer.getBuffer(), postBuffer.size());
        }
    }

    @Override
    public void write(final int b) throws IOException {
        checkPreBufferSize(1);

        if (postBuffer != null) {
            postBuffer.write(b);
        } else {
            preBuffer[preBufferPos] = (byte) b;
            preBufferPos++;
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        checkPreBufferSize(b.length);

        if (postBuffer != null) {
            postBuffer.write(b);
        } else {
            System.arraycopy(b, 0, preBuffer, preBufferPos, b.length);
            preBufferPos = preBufferPos + b.length;
        }

    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        checkPreBufferSize(len);

        if (postBuffer != null) {
            postBuffer.write(b, off, len);
        } else {
            System.arraycopy(b, off, preBuffer, preBufferPos, len);
            preBufferPos = preBufferPos + len;
        }
    }

    private void checkPreBufferSize(final int add) {
        // Not using a post buffer
        if (postBuffer == null) {
            // Exceeded our initial buffer?
            if (preBufferPos + add > preBuffer.length) {
                // Flush our pre buffer
                postBuffer = new GetBufferByteArrayOutputStream();
                postBuffer.write(preBuffer, 0, preBufferPos);
            }
        }
    }

    public static class BufferPos {

        private final byte[] buffer;
        private final int bufferPos;

        @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        public BufferPos(final byte[] buffer, final int bufferPos) {
            this.buffer = buffer;
            this.bufferPos = bufferPos;
        }

        @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        public byte[] getBuffer() {
            return buffer;
        }

        public int getBufferPos() {
            return bufferPos;
        }

        @Override
        public String toString() {
            return new String(buffer, 0, bufferPos, StreamUtil.DEFAULT_CHARSET);
        }
    }

    private static class GetBufferByteArrayOutputStream extends ByteArrayOutputStream {

        @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        public byte[] getBuffer() {
            return buf;
        }
    }

}
