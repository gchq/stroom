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

package stroom.io;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestDelayedCreateOutputStream extends StroomUnitTest {
    @Test
    public void testEmptyStreamNeverGetsCreated() throws Exception {
        doTest(false);
    }

    @Test
    public void testStreamGetsCreated() throws Exception {
        doTest(true);
    }

    private void doTest(final boolean write) throws IOException {
        final Path file = getCurrentTestDir().resolve("DelayedCreateOutputStream.dat");
        FileUtil.deleteFile(file);

        final DelayedCreateOutputStream delayedCreateOutputStream = createTestStream(file);

        Assert.assertFalse(Files.isRegularFile(file));
        if (write) {
            delayedCreateOutputStream.write(1);
        } else {
            delayedCreateOutputStream.flush();
        }
        delayedCreateOutputStream.close();

        Assert.assertEquals(write, Files.isRegularFile(file));

        if (write) {
            FileUtil.deleteFile(file);
        }
    }

    private DelayedCreateOutputStream createTestStream(final Path file) {
        final DelayedCreateOutputStream delayedCreateOutputStream = new DelayedCreateOutputStream() {
            @Override
            protected OutputStream createOutputStream() {
                try {
                    return Files.newOutputStream(file);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return delayedCreateOutputStream;
    }

}
