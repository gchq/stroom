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

package stroom.importexport.server;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;

import stroom.AbstractCoreIntegrationTest;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.FolderService;
import stroom.feed.shared.FeedService;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.resource.server.ResourceStore;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.zip.ZipUtil;

public class TestImportExportServiceImpl2 extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ResourceStore resourceStore;
    @Resource
    private FolderService folderService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private FeedService feedService;

    @Test
    public void testImportZip() throws Exception {
        final File rootTestDir = StroomCoreServerTestFileUtil.getTestResourcesDir();
        final File importDir = new File(rootTestDir, "samples/config");
        final File zipFile = new File(getCurrentTestDir(), UUID.randomUUID().toString() + ".zip");

        ZipUtil.zip(zipFile, importDir, Pattern.compile("Feeds and Translations/Benchmark.*|.*Folder\\.xml"),
                Pattern.compile(".*/\\..*"));

        Assert.assertTrue(zipFile.isFile());
        Assert.assertTrue(importDir.isDirectory());

        final List<ImportState> confirmList = importExportService.createImportConfirmationList(zipFile.toPath());
        Assert.assertNotNull(confirmList);

        importExportService.performImportWithoutConfirmation(zipFile.toPath());
    }
}
