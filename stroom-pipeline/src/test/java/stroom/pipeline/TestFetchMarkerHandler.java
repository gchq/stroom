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

package stroom.pipeline;


import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.util.shared.Summary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFetchMarkerHandler extends StroomUnitTest {

    @Test
    void test() throws IOException {
        doTest(4, 12);

        doTest(7, 12, Severity.WARNING);

        doTest(9, 12, Severity.ERROR);

        doTest(12, 12, Severity.WARNING, Severity.ERROR);
    }

    private void doTest(final int expectedSize, final int expectedTotal, final Severity... expanded)
            throws IOException {
        // Get the testing directory.
        final Path testDataDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestFetchMarkerHandler");
        final Path inFile = testDir.resolve("001.dat");
        final String string = StreamUtil.fileToString(inFile);

        final MarkerListCreator fetchMarkerHandler = new MarkerListCreator();
        final List<Marker> markersList = fetchMarkerHandler.createFullList(new StringReader(string), expanded);

        assertThat(markersList.size()).isEqualTo(expectedSize);

        int total = 0;
        for (final Marker marker : markersList) {
            if (marker instanceof Summary) {
                total += ((Summary) marker).getTotal();
            }
        }

        assertThat(total).isEqualTo(expectedTotal);
    }
}
