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

package stroom.pipeline.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.util.shared.Summary;
import stroom.util.test.StroomUnitTest;

import java.io.File;
import java.io.StringReader;
import java.util.List;

public class TestFetchMarkerHandler extends StroomUnitTest {
    @Test
    public void test() throws Exception {
        doTest(4, 12);

        doTest(7, 12, Severity.WARNING);

        doTest(9, 12, Severity.ERROR);

        doTest(12, 12, Severity.WARNING, Severity.ERROR);
    }

    private void doTest(final int expectedSize, final int expectedTotal, final Severity... expanded) throws Exception {
        // Get the testing directory.
        final File testDataDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final File testDir = new File(testDataDir, "TestFetchMarkerHandler");
        final File inFile = new File(testDir, "001.dat");
        final String string = StreamUtil.fileToString(inFile);

        final MarkerListCreator fetchMarkerHandler = new MarkerListCreator();
        final List<Marker> markersList = fetchMarkerHandler.createFullList(new StringReader(string), expanded);

        Assert.assertEquals(expectedSize, markersList.size());

        int total = 0;
        for (final Marker marker : markersList) {
            if (marker instanceof Summary) {
                total += ((Summary) marker).getTotal();
            }
        }

        Assert.assertEquals(expectedTotal, total);
    }
}
