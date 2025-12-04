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


import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestInitialByteArrayOutputStream {

    @Test
    void testReuse() throws IOException {
        final byte[] reuseBuffer = new byte[2];

        InitialByteArrayOutputStream initialByteArrayOutputStream = null;
        try {
            initialByteArrayOutputStream = new InitialByteArrayOutputStream(reuseBuffer);
            assertThat(initialByteArrayOutputStream.getBufferPos().getBuffer() == reuseBuffer).isTrue();

            initialByteArrayOutputStream.write(1);
            assertThat(initialByteArrayOutputStream.getBufferPos().getBuffer() == reuseBuffer).isTrue();

            initialByteArrayOutputStream.write(1);
            assertThat(initialByteArrayOutputStream.getBufferPos().getBuffer() == reuseBuffer).isTrue();

            initialByteArrayOutputStream.write(1);
            assertThat(initialByteArrayOutputStream.getBufferPos().getBuffer() == reuseBuffer).isFalse();
            assertThat(initialByteArrayOutputStream.getBufferPos().getBuffer().length > 2).isTrue();
        } finally {
            CloseableUtil.close(initialByteArrayOutputStream);
        }

    }

}
