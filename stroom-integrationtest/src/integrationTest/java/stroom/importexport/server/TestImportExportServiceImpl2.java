/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.importexport.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.ImportState;
import stroom.feed.server.FeedService;
import stroom.pipeline.server.PipelineService;
import stroom.resource.server.ResourceStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.zip.ZipUtil;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class TestImportExportServiceImpl2 extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ResourceStore resourceStore;
    @Resource
    private PipelineService pipelineService;
    @Resource
    private FeedService feedService;

    @Test
    public void testImportZip() throws Exception {
        final Path rootTestDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final Path importDir = rootTestDir.resolve("samples/config");
        final Path zipFile = getCurrentTestDir().resolve(UUID.randomUUID().toString() + ".zip");

        ZipUtil.zip(zipFile, importDir, Pattern.compile("Feeds and Translations/Benchmark.*|.*Folder\\.xml"),
                Pattern.compile(".*/\\..*"));

        Assert.assertTrue(Files.isRegularFile(zipFile));
        Assert.assertTrue(Files.isDirectory(importDir));

        final List<ImportState> confirmList = importExportService.createImportConfirmationList(zipFile);
        Assert.assertNotNull(confirmList);

        importExportService.performImportWithoutConfirmation(zipFile);
    }
}
