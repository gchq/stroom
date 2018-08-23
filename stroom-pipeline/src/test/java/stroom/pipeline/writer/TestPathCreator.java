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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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

    @Test
    public void testReplaceTime() {
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(2018, 8, 20, 13, 17, 22, 2111444, ZoneOffset.UTC);

        String path = "${feed}/${year}/${year}-${month}/${year}-${month}-${day}/${pathId}/${id}";

        // Replace pathId variable with path id.
        path = PathCreator.replace(path, "pathId", () -> "1234");
        // Replace id variable with file id.
        path = PathCreator.replace(path, "id", () -> "5678");

        Assert.assertEquals("${feed}/${year}/${year}-${month}/${year}-${month}-${day}/1234/5678", path);

        path = PathCreator.replaceTimeVars(path, zonedDateTime);

        Assert.assertEquals("${feed}/2018/2018-08/2018-08-20/1234/5678", path);
    }
}
