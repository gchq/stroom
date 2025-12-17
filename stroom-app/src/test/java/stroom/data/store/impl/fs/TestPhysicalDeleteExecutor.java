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

package stroom.data.store.impl.fs;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.PhysicalDeleteExecutor.Progress;
import stroom.db.util.JooqUtil;
import stroom.meta.api.MetaService;
import stroom.meta.impl.db.MetaDbConnProvider;
import stroom.meta.shared.Meta;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.meta.impl.db.jooq.tables.Meta.META;

class TestPhysicalDeleteExecutor extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPhysicalDeleteExecutor.class);

    private static final stroom.meta.impl.db.jooq.tables.Meta meta = META.as("m");

    @Inject
    private FsFileFinder fileFinder;
    @Inject
    private MetaService metaService;
    @Inject
    private DataVolumeService dataVolumeService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private PhysicalDeleteExecutor physicalDeleteExecutor;
    @Inject
    private Provider<DataStoreServiceConfig> dataStoreServiceConfigProvider;
    @Inject
    private MetaDbConnProvider metaDbConnProvider;

    @AfterEach
    void unsetProperties() {
        clearConfigValueMapper();
    }

    @Test
    void test_oneBatch() {
        setConfigValueMapper(DataStoreServiceConfig.class, config ->
                config.withDeletePurgeAge(StroomDuration.ZERO));

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta md1 = create(feedName);
        final Meta md2 = create(feedName);

        final List<Path> files1 = fileFinder.findAllStreamFile(md1);
        final List<Path> files2 = fileFinder.findAllStreamFile(md2);

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

        // Run twice to ensure the end state is the same and it copes with being re-run
        for (int i = 1; i <= 2; i++) {
            LOGGER.info("Run {}", i);
            // Run physical delete.
            physicalDeleteExecutor.exec();

            // Check files.
            countFiles(files1, 0);
            countFiles(files2, 2);
            countDataVolume(md1, 0);
            countDataVolume(md2, 1);
        }
    }

    @Test
    void test_noData() {
        setConfigValueMapper(DataStoreServiceConfig.class, config ->
                config.withDeletePurgeAge(StroomDuration.ofDays(30)));

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta md1 = create(feedName);
        final Meta md2 = create(feedName);

        final List<Path> files1 = fileFinder.findAllStreamFile(md1);
        final List<Path> files2 = fileFinder.findAllStreamFile(md2);

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

        // Run twice to ensure the end state is the same and it copes with being re-run
        for (int i = 1; i <= 2; i++) {
            LOGGER.info("Run {}", i);

            // Run physical delete.
            physicalDeleteExecutor.exec();

            // Check files.
            countFiles(files1, 2);
            countFiles(files2, 2);
            countDataVolume(md1, 1);
            countDataVolume(md2, 1);
        }
    }

    @Test
    void test_twoBatches() {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withDeletePurgeAge(StroomDuration.ZERO)
                .withDeleteBatchSize(5));

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final int totalMetaCount = 20;
        final int deletedMetaCount = totalMetaCount - 8;

        final List<Meta> metas = IntStream.rangeClosed(1, totalMetaCount)
                .boxed()
                .map(i -> create(feedName))
                .collect(Collectors.toList());

        for (final Meta meta : metas) {
            final List<Path> files = fileFinder.findAllStreamFile(meta);
            countFiles(files, 2);
            countDataVolume(meta, 1);
        }

        final List<Meta> deletedMetas = metas.stream()
                .limit(deletedMetaCount)
                .peek(meta -> metaService.delete(meta.getId()))
                .collect(Collectors.toList());

        final List<Meta> unlockedMetas = metas
                .stream()
                .filter(meta -> !deletedMetas.contains(meta))
                .collect(Collectors.toList());

        assertThat(deletedMetas)
                .hasSize(deletedMetaCount);

        for (final Meta meta : metas) {
            final List<Path> files = fileFinder.findAllStreamFile(meta);
            countFiles(files, 2);
            countDataVolume(meta, 1);
        }

        // Run twice to ensure the end state is the same and it copes with being re-run
        for (int i = 1; i <= 2; i++) {
            LOGGER.info("Run {}", i);

            // Run physical delete.
            final Progress progress = Progress.start(dataStoreServiceConfigProvider.get());
            physicalDeleteExecutor.delete(Instant.now().plusSeconds(10), progress);

            progress.logSummaryToInfo("End of run " + i + " summary");

            if (i == 1) {
                assertThat(progress.getSuccessCount())
                        .isEqualTo(deletedMetaCount);
                assertThat(progress.getFileDeleteCount())
                        .isEqualTo(deletedMetaCount * 2); // 2 files per meta
                assertThat(progress.getFailureCount())
                        .isEqualTo(0); // 2 files per meta
                assertThat(progress.getBatchCount())
                        .isEqualTo(3); // 5, 5, 2
            } else if (i == 2) {
                // 2nd pass so nowt to do
                assertThat(progress.getSuccessCount())
                        .isEqualTo(0);
                assertThat(progress.getFileDeleteCount())
                        .isEqualTo(0);
                assertThat(progress.getFailureCount())
                        .isEqualTo(0);
                assertThat(progress.getBatchCount())
                        .isEqualTo(0);
            }

            for (final Meta unlockedMeta : unlockedMetas) {
                final List<Path> files = fileFinder.findAllStreamFile(unlockedMeta);
                countFiles(files, 2);
                countDataVolume(unlockedMeta, 1);
            }

            for (final Meta deletedMeta : deletedMetas) {
                final List<Path> files = fileFinder.findAllStreamFile(deletedMeta);
                countFiles(files, 0);
                countDataVolume(deletedMeta, 0);
            }
        }
    }

    @Test
    void test_someOldEnough() {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withDeletePurgeAge(StroomDuration.ZERO)
                .withDeleteBatchSize(5));

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final int totalMetaCount = 30;
        final int deletedMetaCount = totalMetaCount - 10;
        final int oldMetaCount = 10;

        final List<Meta> metas = IntStream.rangeClosed(1, totalMetaCount)
                .boxed()
                .map(i -> create(feedName))
                .collect(Collectors.toList());

        for (final Meta meta : metas) {
            final List<Path> files = fileFinder.findAllStreamFile(meta);
            countFiles(files, 2);
            countDataVolume(meta, 1);
        }

        final List<Meta> deletedMetas = metas.stream()
                .limit(deletedMetaCount)
                .peek(meta -> metaService.delete(meta.getId()))
                .collect(Collectors.toList());

        final List<Meta> oldMetas = deletedMetas.stream()
                .limit(oldMetaCount)
                .collect(Collectors.toList());
        final Instant oldStatusTime = Instant.now().minus(30, ChronoUnit.DAYS);

        JooqUtil.context(metaDbConnProvider, context -> context
                .update(META)
                .set(META.STATUS_TIME, oldStatusTime.toEpochMilli())
                .where(META.ID.in(oldMetas.stream().map(Meta::getId).collect(Collectors.toList())))
                .execute());

        final List<Meta> unlockedMetas = metas
                .stream()
                .filter(meta -> !deletedMetas.contains(meta))
                .collect(Collectors.toList());

        assertThat(deletedMetas)
                .hasSize(deletedMetaCount);
        assertThat(oldMetas)
                .hasSize(oldMetaCount);

        for (final Meta meta : metas) {
            final List<Path> files = fileFinder.findAllStreamFile(meta);
            countFiles(files, 2);
            countDataVolume(meta, 1);
        }

        // Run twice to ensure the end state is the same and it copes with being re-run
        for (int i = 1; i <= 2; i++) {
            LOGGER.info("Run {}", i);

            // Run physical delete.
            final Progress progress = Progress.start(dataStoreServiceConfigProvider.get());
            // threshold should be after our old ones but before the other deleted ones
            physicalDeleteExecutor.delete(Instant.now()
                    .minus(10, ChronoUnit.MINUTES), progress);

            progress.logSummaryToInfo("End of run " + i + " summary");

            if (i == 1) {
                assertThat(progress.getSuccessCount())
                        .isEqualTo(oldMetaCount);
                assertThat(progress.getFileDeleteCount())
                        .isEqualTo(oldMetaCount * 2); // 2 files per meta
                assertThat(progress.getFailureCount())
                        .isEqualTo(0); // 2 files per meta
                assertThat(progress.getBatchCount())
                        .isEqualTo(2); // 5, 5
            } else if (i == 2) {
                // 2nd pass so nowt to do
                assertThat(progress.getSuccessCount())
                        .isEqualTo(0);
                assertThat(progress.getFileDeleteCount())
                        .isEqualTo(0);
                assertThat(progress.getFailureCount())
                        .isEqualTo(0);
                assertThat(progress.getBatchCount())
                        .isEqualTo(0);
            }

            for (final Meta unlockedMeta : unlockedMetas) {
                final List<Path> files = fileFinder.findAllStreamFile(unlockedMeta);
                countFiles(files, 2);
                countDataVolume(unlockedMeta, 1);
            }

            for (final Meta deletedMeta : deletedMetas) {
                if (!oldMetas.contains(deletedMeta)) {
                    // These ones are logically deleted but too young for phys delete
                    // so are still there
                    final List<Path> files = fileFinder.findAllStreamFile(deletedMeta);
                    countFiles(files, 2);
                    countDataVolume(deletedMeta, 1);
                }
            }

            for (final Meta oldMeta : oldMetas) {
                final List<Path> files = fileFinder.findAllStreamFile(oldMeta);
                countFiles(files, 0);
                countDataVolume(oldMeta, 0);
            }
        }
    }

    private Meta create(final String feedName) {
        final Meta meta = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, meta);
        return meta;
    }

    private void countDataVolume(final Meta meta, final int expected) {
        final FindDataVolumeCriteria findStreamVolumeCriteria = FindDataVolumeCriteria.create(meta);
        assertThat(dataVolumeService.find(findStreamVolumeCriteria).size())
                .isEqualTo(expected);
    }

    private void countFiles(final List<Path> files, final int expected) {
        final long count = files.stream().filter(Files::isRegularFile).count();
        assertThat(count)
                .isEqualTo(expected);
    }
}
