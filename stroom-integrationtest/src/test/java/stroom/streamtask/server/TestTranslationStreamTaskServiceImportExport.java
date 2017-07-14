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

package stroom.streamtask.server;

import org.junit.Ignore;
import org.junit.Test;
import stroom.test.AbstractCoreIntegrationTest;

@Ignore("TODO 2015-10-21: Restore tests or delete.")
public class TestTranslationStreamTaskServiceImportExport extends AbstractCoreIntegrationTest {
    @Test
    public void test() {
    }
    // @Resource
    // private CommonTestControl commonTestControl;
    // @Resource
    // private CommonTestScenarioCreator commonTestScenarioCreator;
    // @Resource
    // private TranslationStreamTaskService translationStreamTaskService;
    // @Resource
    // private StreamTaskCreator streamTaskCreator;
    // @Resource
    // private FeedService feedService;
    // @Resource
    // private TaskManager taskManager;
    //
    // /**
    // * Test set up.
    // */
    // @Before
    // public void setUp() {
    // commonTestControl.deleteAll();
    // }
    //
    // /**
    // * Changed this test as we no longer expect the API to return ordered
    // items.
    // */
    // @Test
    // public void testExportImport() throws Exception {
    // EventFeed efd1 = commonTestScenarioCreator.createSimpleEventFeed();
    //
    // feedService.save(efd1);
    //
    // Stream raw = commonTestScenarioCreator.createSample2LineRawFile(efd1);
    //
    // // Create tasks.
    // streamTaskCreator.createAllTasks();
    //
    // Assert.assertEquals(1, commonTestControl
    // .countEntity(TranslationStreamTask.class));
    //
    // FindTranslationStreamTaskCriteria criteria =
    // FindTranslationStreamTaskCriteria
    // .createWithStream(raw);
    //
    // TranslationStreamTask translationStreamTask =
    // translationStreamTaskService
    // .find(criteria).getFirst();
    //
    // Stream processsed = commonTestScenarioCreator
    // .createSampleBlankProcessedFile(efd1);
    //
    // translationStreamTask.setComplete();
    // translationStreamTask.setTargetStream(processsed);
    //
    // translationStreamTaskService.save(translationStreamTask);
    //
    // File testDir = FileSystemTestUtil.getTestDir();
    //
    // File testFile = new File(testDir, "export.zip");
    //
    // final TranslationStreamTaskExportTask exportTask = new
    // TranslationStreamTaskExportTask(
    // null, null, criteria, true, true, testFile,
    // new TranslationStreamTaskServiceImportExportSettings());
    // taskManager.exec(exportTask);
    //
    // Assert.assertTrue(testFile.isFile());
    //
    // StroomZipFile stroomZipFile = new StroomZipFile(testFile);
    //
    // Assert.assertEquals(2, stroomZipFile.getStroomZipNameSet().getBaseNameSet()
    // .size());
    // Assert.assertTrue(stroomZipFile.containsEntry("001", StroomZipFileType.Data));
    // Assert.assertTrue(stroomZipFile.containsEntry("002", StroomZipFileType.Data));
    // Assert.assertTrue(stroomZipFile.containsEntry("001", StroomZipFileType.Meta));
    // Assert.assertTrue(stroomZipFile.containsEntry("002", StroomZipFileType.Meta));
    //
    // stroomZipFile.close();
    //
    // Assert.assertEquals(1, translationStreamTaskService.find(
    // new FindTranslationStreamTaskCriteria()).size());
    //
    // // Create another file
    // commonTestScenarioCreator.createSample2LineRawFile(efd1);
    //
    // final TranslationStreamTaskImportTask importTask = new
    // TranslationStreamTaskImportTask(
    // null, null, testFile, true);
    // taskManager.exec(importTask);
    // taskManager.exec(importTask);
    // taskManager.exec(importTask);
    //
    // // Create tasks.
    // streamTaskCreator.createAllTasks();
    //
    // Assert.assertEquals(5, translationStreamTaskService.find(
    // new FindTranslationStreamTaskCriteria()).size());
    // }
}
