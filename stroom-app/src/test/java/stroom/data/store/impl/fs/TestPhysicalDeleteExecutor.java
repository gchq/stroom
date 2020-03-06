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


import org.junit.jupiter.api.Test;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.meta.api.MetaService;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.time.StroomDuration;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestPhysicalDeleteExecutor extends AbstractCoreIntegrationTest {
    @Inject
    private FsDataStoreMaintenanceService streamMaintenanceService;
    @Inject
    private MetaService metaService;
    @Inject
    private DataVolumeService dataVolumeService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private PhysicalDeleteExecutor physicalDeleteExecutor;
    @Inject
    private DataStoreServiceConfig dataStoreServiceConfig;

    @Test
    void test() {
        dataStoreServiceConfig.setDeletePurgeAge(StroomDuration.ZERO);

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta md1 = create(feedName);
        final Meta md2 = create(feedName);

        final List<Path> files1 = streamMaintenanceService.findAllStreamFile(md1);
        final List<Path> files2 = streamMaintenanceService.findAllStreamFile(md2);

        countFiles(files1, 2);
        countFiles(files2, 2);
        countDataVolume(md1, 1);
        countDataVolume(md2, 1);

        // Tell the meta service to logically delete the item.
        metaService.delete(md1.getId());

        // Check we still have files.
        countFiles(files1, 2);
        countFiles(files2, 2);
        countDataVolume(md1, 1);
        countDataVolume(md2, 1);

        // Run physical delete.
        physicalDeleteExecutor.exec();

        // Check files.
        countFiles(files1, 0);
        countFiles(files2, 2);
        countDataVolume(md1, 0);
        countDataVolume(md2, 1);
    }

    private Meta create(final String feedName) {
        final Meta meta = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, meta);
        return meta;
    }

    private void countDataVolume(final Meta meta, final int expected) {
        final FindDataVolumeCriteria findStreamVolumeCriteria = FindDataVolumeCriteria.create(meta);
        assertThat(dataVolumeService.find(findStreamVolumeCriteria).size()).isEqualTo(expected);
    }

    private void countFiles(final List<Path> files, final int expected) {
        final long count = files.stream().filter(Files::isRegularFile).count();
        assertThat(count).isEqualTo(expected);
    }
}
