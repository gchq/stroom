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

package stroom.gitrepo.impl;

import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.eclipse.jgit.util.TemporaryBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An Apache entity backed by a JGit {@link TemporaryBuffer}.
 * <p>
 * JGit hands us a request body by asking for an {@link OutputStream} and writing into it, so the body has to
 * be held somewhere before it can be sent. {@link TemporaryBuffer.LocalFile} is the right somewhere: it keeps
 * small bodies in memory and spills large ones to a temp file, which matters because a push can be arbitrarily
 * large and buffering one in heap would not end well.
 * </p>
 * <p>
 * Being backed by a buffer rather than a stream also makes this repeatable, so the client is free to replay
 * the request after an authentication challenge or a redirect - which it will, because Git over HTTP expects
 * to be challenged.
 * </p>
 */
class TemporaryBufferEntity extends AbstractHttpEntity {

    private final TemporaryBuffer buffer;
    private final long contentLength;

    /**
     * @param contentLength The known length, or -1 for chunked encoding.
     */
    TemporaryBufferEntity(final TemporaryBuffer buffer,
                          final long contentLength,
                          final String contentType) {
        super(contentType, null, contentLength < 0);
        this.buffer = buffer;
        this.contentLength = contentLength;
    }

    TemporaryBuffer getBuffer() {
        return buffer;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return contentLength >= 0
                ? contentLength
                : buffer.length();
    }

    @Override
    public InputStream getContent() throws IOException {
        return buffer.openInputStream();
    }

    @Override
    public void writeTo(final OutputStream outputStream) throws IOException {
        buffer.writeTo(outputStream, null);
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public void close() {
        buffer.destroy();
    }
}
