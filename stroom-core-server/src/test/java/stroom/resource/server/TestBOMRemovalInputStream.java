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
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.ZipResource;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

// TODO : Add test data
@Ignore("Add test data")
@RunWith(StroomJUnit4ClassRunner.class)
public class TestBOMRemovalInputStream extends StroomUnitTest {
    @ClassRule
    public static ZipResource bomBlank = new ZipResource("stroom/resource/server/BOM_BLANK");
    @ClassRule
    public static ZipResource bomContent = new ZipResource("stroom/resource/server/BOM_CONTENT");

    @Test
    public void testBlank() throws Exception {
        final LineNumberReader lineNumberReader = getLineNumberReader(bomBlank);
        Assert.assertNull(lineNumberReader.readLine());
        lineNumberReader.close();
    }

    @Test
    public void testContent() throws Exception {
        final LineNumberReader lineNumberReader = getLineNumberReader(bomContent);
        Assert.assertNotNull(lineNumberReader.readLine());
        lineNumberReader.close();
    }

    private LineNumberReader getLineNumberReader(final ZipResource zipResource) throws Exception {
        final ZipInputStream zipInputStream = zipResource.getZipInputStream();
        zipInputStream.getNextEntry();

        final LineNumberReader lineNumberReader = new LineNumberReader(new InputStreamReader(
                new BOMRemovalInputStream(zipInputStream, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8));
        return lineNumberReader;
    }
}
