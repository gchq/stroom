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

package stroom.streamstore.server.fs;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestFileSystemPrefixUtil extends StroomUnitTest {
    @Test
    public void testPadId() {
        Assert.assertEquals("000", FileSystemPrefixUtil.padId(null));
        Assert.assertEquals("000", FileSystemPrefixUtil.padId(Long.valueOf(0)));
        Assert.assertEquals("001", FileSystemPrefixUtil.padId(Long.valueOf(1L)));
        Assert.assertEquals("001001", FileSystemPrefixUtil.padId(Long.valueOf(1001L)));
    }

    @Test
    public void testBuildIdPath() {
        Assert.assertEquals("000", FileSystemPrefixUtil.buildIdPath("000000"));
        Assert.assertNull(FileSystemPrefixUtil.buildIdPath(FileSystemPrefixUtil.padId(1L)));
        Assert.assertEquals("009", FileSystemPrefixUtil.buildIdPath(FileSystemPrefixUtil.padId(9999L)));
    }

}
