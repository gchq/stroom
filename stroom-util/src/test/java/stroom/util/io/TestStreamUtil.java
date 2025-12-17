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


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamUtil {

    private static final int SIZE = 25;

    @Test
    void testFullRead() throws IOException {
        final byte[] buffer = new byte[10];

        final InputStream testStream = new TestInputStream();
        Assertions.assertThat(StreamUtil.eagerRead(testStream, buffer)).isEqualTo(10);
        assertThat(StreamUtil.eagerRead(testStream, buffer)).isEqualTo(10);
        assertThat(StreamUtil.eagerRead(testStream, buffer)).isEqualTo(5);
        assertThat(StreamUtil.eagerRead(testStream, buffer)).isEqualTo(-1);
        assertThat(StreamUtil.eagerRead(testStream, buffer)).isEqualTo(-1);
    }

    @Test
    void testException() {
        try {
            throw new RuntimeException();
        } catch (final RuntimeException ex) {
            final String callStack = StreamUtil.exceptionCallStack(ex);
            assertThat(callStack.contains("testException")).as(callStack).isTrue();
        }
    }

    private static class TestInputStream extends InputStream {

        int read = 0;

        @Override
        public int read() throws IOException {
            read++;
            if (read > SIZE) {
                return -1;
            }
            return 1;
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return read(b, 0, 1);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            return super.read(b, off, 1);
        }
    }
}
