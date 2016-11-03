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

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.io.FileUtil;
import stroom.util.shared.ResourceKey;
import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

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

        FileUtil.createNewFile(resourceStore.getTempFile(key1));
        FileUtil.createNewFile(resourceStore.getTempFile(key2));

        Assert.assertTrue(resourceStore.getTempFile(key1).isFile());
        Assert.assertTrue(resourceStore.getTempFile(key2).isFile());

        // Roll to Old
        resourceStore.execute();
        final File file1 = resourceStore.getTempFile(key1);
        Assert.assertTrue(file1.isFile());
        final File file2 = resourceStore.getTempFile(key2);
        Assert.assertTrue(file2.isFile());

        // Roll to Delete
        resourceStore.execute();
        Assert.assertNull(resourceStore.getTempFile(key1));
        Assert.assertFalse(file1.isFile());
        Assert.assertNull(resourceStore.getTempFile(key2));
        Assert.assertFalse(file2.isFile());
    }
}
