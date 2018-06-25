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

package stroom.data.store.impl.fs;

import org.junit.Assert;
import org.junit.Test;
import stroom.data.meta.api.Stream;
import stroom.data.store.FindStreamVolumeCriteria;
import stroom.data.volume.api.StreamVolumeService;
import stroom.jobsystem.MockTask;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestFileSystemStreamMaintenanceService extends AbstractCoreIntegrationTest {
    @Inject
    private FileSystemStreamMaintenanceService streamMaintenanceService;
    @Inject
    private StreamVolumeService streamVolumeService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;

    @Test
    public void testSimple() throws IOException {
        // commonTestControl.deleteDir();

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Stream md = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, md);

        final List<Path> files = streamMaintenanceService.findAllStreamFile(md);

        Assert.assertTrue(files.size() > 0);

        final FindStreamVolumeCriteria findStreamVolumeCriteria = FindStreamVolumeCriteria.create(md);
        Assert.assertTrue(streamVolumeService.find(findStreamVolumeCriteria).size() > 0);

        final Path dir = files.iterator().next().getParent();

        final Path test1 = dir.resolve("badfile.dat");

        Files.createFile(test1);

        fileSystemCleanTaskExecutor.exec(new MockTask("Test"));
    }
}
