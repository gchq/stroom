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

package stroom.processor.impl;

import stroom.test.AbstractCoreIntegrationTest;

import org.junit.jupiter.api.Test;

class TestTranslationStreamTaskServiceImportExport extends AbstractCoreIntegrationTest {

    @Test
    void test() {
    }
    // @Inject
    // private CommonTestControl commonTestControl;
    // @Inject
    // private CommonTestScenarioCreator commonTestScenarioCreator;
    // @Inject
    // private TranslationStreamTaskService translationStreamTaskService;
    // @Inject
    // private ProcessorTaskManager processorTaskManager;
    // @Inject
    // private FeedService feedService;
    // @Inject
    // private TaskManager taskManager;
    //
    // /**
    // * Test set up.
    // */
    // @BeforeEach
    // public void setUp() {
    // commonTestControl.deleteAll();
    // }
    //
    // /**
    // * Changed this test as we no longer expect the API to return ordered
    // items.
    // */
    // @Test
    // public void testExportImport() {
    // EventFeed efd1 = commonTestScenarioCreator.createSimpleEventFeed();
    //
    // feedService.save(efd1);
    //
    // Stream raw = commonTestScenarioCreator.createSample2LineRawFile(efd1);
    //
    // // Create tasks.
    // processorTaskManager.createAllTasks();
    //
    // assertThat(commonTestControl
    // .countEntity(TranslationStreamTask.class)).isEqualTo(1);
    //
    // FindTranslationStreamTaskCriteria criteria =
    // FindTranslationStreamTaskCriteria
    // .createFromMeta(raw);
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
    // Path testDir = FileSystemTestUtil.getTestDir();
    //
    // Path testFile = testDir.resolve("export.zip");
    //
    // final TranslationStreamTaskExportTask exportTask = new
    // TranslationStreamTaskExportTask(
    // null, null, criteria, true, true, testFile,
    // new TranslationStreamTaskServiceImportExportSettings());
    // taskManager.exec(exportTask);
    //
    // assertThat(testFile.isFile()).isTrue();
    //
    // try (final StroomZipFile stroomZipFile = new StroomZipFile(testFile);
    //
    // assertThat(stroomZipFile.getStroomZipNameSet().getBaseNameSet()
    // .size()).isEqualTo(2);
    // assertThat(stroomZipFile.containsEntry("001", StroomZipFileType.Data)).isTrue();
    // assertThat(stroomZipFile.containsEntry("002", StroomZipFileType.Data)).isTrue();
    // assertThat(stroomZipFile.containsEntry("001", StroomZipFileType.Meta)).isTrue();
    // assertThat(stroomZipFile.containsEntry("002", StroomZipFileType.Meta)).isTrue();
    //
    // stroomZipFile.close();
    //
    // assertThat(translationStreamTaskService.find(
    // new FindTranslationStreamTaskCriteria()).size()).isEqualTo(1);
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
    // processorTaskManager.createAllTasks();
    //
    // assertThat(translationStreamTaskService.find(
    // new FindTranslationStreamTaskCriteria()).size()).isEqualTo(5);
    // }
}
