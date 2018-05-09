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

package stroom.util.thread;

import org.springframework.util.StringUtils;

/**
 * Buffer held in thread scope for performance reasons.
 */
public class ThreadLocalBuffer {
    /**
     * Same size as JDK's Buffered Output Stream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private String bufferSize = null;

    private ThreadLocal<byte[]> threadLocalBuffer = ThreadLocal.withInitial(() -> new byte[getDerivedBufferSize()]);

    public byte[] getBuffer() {
        return threadLocalBuffer.get();
    }

    public void setBufferSize(String bufferSize) {
        this.bufferSize = bufferSize;
    }

    private int getDerivedBufferSize() {
        if (!StringUtils.hasText(bufferSize)) {
            return DEFAULT_BUFFER_SIZE;
        } else {
            return Integer.parseInt(bufferSize);
        }
    }
}
