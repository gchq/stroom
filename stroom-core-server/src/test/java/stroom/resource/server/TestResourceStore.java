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

package stroom.resource.server;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.shared.ResourceKey;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestResourceStore extends StroomUnitTest {
    @Test
    public void testSimple() throws IOException {
        final ResourceStoreImpl resourceStore = new ResourceStoreImpl();
        getCurrentTestDir();
        resourceStore.execute();

        final ResourceKey key1 = resourceStore.createTempFile("TestResourceStore1.dat");
        Assert.assertTrue(key1.toString().endsWith("TestResourceStore1.dat"));

        final ResourceKey key2 = resourceStore.createTempFile("TestResourceStore2.dat");
        Assert.assertTrue(key2.toString().endsWith("TestResourceStore2.dat"));

        Files.createFile(resourceStore.getTempFile(key1));
        Files.createFile(resourceStore.getTempFile(key2));

        Assert.assertTrue(Files.isRegularFile(resourceStore.getTempFile(key1)));
        Assert.assertTrue(Files.isRegularFile(resourceStore.getTempFile(key2)));

        // Roll to Old
        resourceStore.execute();
        final Path file1 = resourceStore.getTempFile(key1);
        Assert.assertTrue(Files.isRegularFile(file1));
        final Path file2 = resourceStore.getTempFile(key2);
        Assert.assertTrue(Files.isRegularFile(file2));

        // Roll to Delete
        resourceStore.execute();
        Assert.assertNull(resourceStore.getTempFile(key1));
        Assert.assertFalse(Files.isRegularFile(file1));
        Assert.assertNull(resourceStore.getTempFile(key2));
        Assert.assertFalse(Files.isRegularFile(file2));
    }
}
