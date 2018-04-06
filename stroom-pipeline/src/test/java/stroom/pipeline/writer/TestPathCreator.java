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

package stroom.pipeline.writer;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.test.StroomUnitTest;

public class TestPathCreator extends StroomUnitTest {

    @Test
    public void testReplaceFileName() {
        Assert.assertEquals("test.txt", PathCreator.replaceFileName("${fileStem}.txt", "test.tmp"));

        Assert.assertEquals("test", PathCreator.replaceFileName("${fileStem}", "test.tmp"));

        Assert.assertEquals("test", PathCreator.replaceFileName("${fileStem}", "test"));

        Assert.assertEquals("tmp", PathCreator.replaceFileName("${fileExtension}", "test.tmp"));

        Assert.assertEquals("", PathCreator.replaceFileName("${fileExtension}", "test"));

        Assert.assertEquals("test.tmp.txt", PathCreator.replaceFileName("${fileName}.txt", "test.tmp"));
    }

    @Test
    public void testFindVars() {
        final String[] vars = PathCreator.findVars("/temp/${feed}-FLAT/${pipe}_less-${uuid}/${searchId}");
        Assert.assertEquals(4, vars.length);
        Assert.assertEquals("feed", vars[0]);
        Assert.assertEquals("pipe", vars[1]);
        Assert.assertEquals("uuid", vars[2]);
        Assert.assertEquals("searchId", vars[3]);
    }
}
