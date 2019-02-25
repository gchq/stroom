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

package stroom.pipeline.reader;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import stroom.test.common.util.ZipResource;
import stroom.test.common.util.test.StroomUnitTest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

// TODO : Add test data
@Disabled
class TestBOMRemovalInputStream extends StroomUnitTest {
    private static ZipResource bomBlank = new ZipResource("stroom/resource/server/BOM_BLANK");
    private static ZipResource bomContent = new ZipResource("stroom/resource/server/BOM_CONTENT");

    @BeforeAll
    static void before() throws IOException {
        bomBlank.before();
        bomContent.before();
    }

    @AfterAll
    static void after() {
        bomBlank.after();
        bomContent.after();
    }

    @Test
    void testBlank() throws IOException {
        final LineNumberReader lineNumberReader = getLineNumberReader(bomBlank);
        assertThat(lineNumberReader.readLine()).isNull();
        lineNumberReader.close();
    }

    @Test
    void testContent() throws IOException {
        final LineNumberReader lineNumberReader = getLineNumberReader(bomContent);
        assertThat(lineNumberReader.readLine()).isNotNull();
        lineNumberReader.close();
    }

    private LineNumberReader getLineNumberReader(final ZipResource zipResource) throws IOException {
        final ZipInputStream zipInputStream = zipResource.getZipInputStream();
        zipInputStream.getNextEntry();

        return new LineNumberReader(new InputStreamReader(
                new BOMRemovalInputStream(zipInputStream, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8));
    }
}
