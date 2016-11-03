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

package stroom.util.io;

import stroom.util.test.StroomJUnit4ClassRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStreamUtil {
    private static final int SIZE = 25;

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
        public int read(byte[] b) throws IOException {
            return read(b, 0, 1);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, 1);
        }
    }

    @Test
    public void testFullRead() throws IOException {
        byte[] buffer = new byte[10];

        InputStream testStream = new TestInputStream();
        Assert.assertEquals(10, StreamUtil.eagerRead(testStream, buffer));
        Assert.assertEquals(10, StreamUtil.eagerRead(testStream, buffer));
        Assert.assertEquals(5, StreamUtil.eagerRead(testStream, buffer));
        Assert.assertEquals(-1, StreamUtil.eagerRead(testStream, buffer));
        Assert.assertEquals(-1, StreamUtil.eagerRead(testStream, buffer));
    }

    @Test
    public void testException() {
        try {
            throw new RuntimeException();
        } catch (RuntimeException ex) {
            String callStack = StreamUtil.exceptionCallStack(ex);
            Assert.assertTrue(callStack, callStack.contains("testException"));
        }
    }
}
