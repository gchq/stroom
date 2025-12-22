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

import stroom.cache.impl.CacheModule;
import stroom.cache.service.impl.CacheServiceModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.db.FsDataStoreDaoModule;
import stroom.data.store.impl.fs.db.FsDataStoreDbModule;
import stroom.event.logging.api.DocumentEventLog;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.SimpleMetaImpl;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.guice.AbstractTestModule;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestFsPathHelperIntegration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFsPathHelperIntegration.class);

    @Inject
    private FsPathHelper fsPathHelper;

    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        this.tempDir = tempDir;

        final AbstractModule localModule = new AbstractTestModule() {
            @Override
            protected void configure() {
                bindMock(DocumentEventLog.class);
                bindMock(EntityEventBus.class);
                bind(HomeDirProvider.class).toInstance(() -> tempDir);
                bind(TempDirProvider.class).toInstance(() -> tempDir);
            }
        };

        Guice.createInjector(
                        localModule,
                        new FsDataStoreDbModule(),
                        new FsDataStoreDaoModule(),
                        new MockClusterLockModule(),
                        new MockTaskModule(),
                        new MockSecurityContextModule(),
                        new MockMetricsModule(),
                        new CacheModule(),
                        new CacheServiceModule(),
                        new DbTestModule())
                .injectMembers(this);
    }

    @Test
    void testGetRootPath() throws IOException {
        final Path volPath = Path.of("/some/path");
        final long createMs = LocalDate.of(2023, 3, 17)
                .atTime(LocalTime.MIDNIGHT)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        final String streamType = StreamTypeNames.RAW_EVENTS;
        final String feed = "FEED_ME";
        final long metaId = 123_456_789L;
        final SimpleMeta simpleMeta = new SimpleMetaImpl(
                metaId,
                streamType,
                feed,
                createMs,
                createMs);
        final FsVolumeConfig fsVolumeConfig = new FsVolumeConfig();
        final String metaTypeExt = fsVolumeConfig.getMetaTypeExtension(streamType).orElseThrow();

        final Path rootPath = fsPathHelper.getRootPath(volPath, simpleMeta);

        final List<String> pathParts = new ArrayList<>(rootPath.getNameCount());
        for (final Path path : rootPath) {
            pathParts.add(path.getFileName().toString());
        }

        assertThat(pathParts)
                .containsExactly(
                        "some",
                        "path",
                        "store",
                        streamType.toUpperCase().replace(' ', '_'),
                        "2023",
                        "03",
                        "17",
                        "123", // 1st 3 digits of metaId
                        "456", // 2st 3 digits of metaId
                        feed + FsPathHelper.FILE_SEPARATOR_CHAR + metaId + "." + metaTypeExt + ".bgz");

        LOGGER.info("rootPath: {}", rootPath);
    }

    @Disabled
    @Test
    void testGetRootPath_performance() throws IOException {
        final Path volPath = Path.of("/some/path");
        final long createMs = LocalDate.of(2023, 3, 17)
                .atTime(LocalTime.MIDNIGHT)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        final String streamType = StreamTypeNames.RAW_EVENTS;
        final String feed = "FEED_ME";
        final long metaId = 123_456_789L;
        final SimpleMeta simpleMeta = new SimpleMetaImpl(
                metaId,
                streamType,
                feed,
                createMs,
                createMs);
        final FsVolumeConfig fsVolumeConfig = new FsVolumeConfig();
        final String metaTypeExt = fsVolumeConfig.getMetaTypeExtension(streamType).orElseThrow();

        Path rootPath = fsPathHelper.getRootPath(volPath, simpleMeta);

        final List<String> pathParts = new ArrayList<>(rootPath.getNameCount());
        for (final Path path : rootPath) {
            pathParts.add(path.getFileName().toString());
        }

        assertThat(pathParts)
                .containsExactly(
                        "some",
                        "path",
                        "store",
                        streamType.toUpperCase().replace(' ', '_'),
                        "2023",
                        "03",
                        "17",
                        "123", // 1st 3 digits of metaId
                        "456", // 2st 3 digits of metaId
                        feed + FsPathHelper.FILE_SEPARATOR_CHAR + metaId + "." + metaTypeExt + ".bgz");

        LOGGER.info("rootPath: {}", rootPath);

        // Warm up
        for (int i = 0; i < 1_000; i++) {
            rootPath = fsPathHelper.getRootPath(volPath, simpleMeta);
        }

        // Give visualvm chance to load up
//        ThreadUtil.sleepIgnoringInterrupts(10_000);

//        System.out.println("Press enter in console to start run");
//        System.in.read();
//
//        LOGGER.info("Starting work");
        final int iterations = 1981;

        final List<SimpleMeta> metaList = IntStream.rangeClosed(1, iterations)
                .boxed()
                .map(i -> new SimpleMetaImpl(
                        i,
                        streamType,
                        feed,
                        createMs,
                        createMs)
                )
                .collect(Collectors.toList());

        final DurationTimer durationTimer = DurationTimer.start();
        for (final SimpleMeta meta : metaList) {
            rootPath = fsPathHelper.getRootPath(
                    volPath,
                    meta);
        }
        LOGGER.info("took {} for {} iterations, avg: {}",
                durationTimer, iterations, durationTimer.get().dividedBy(iterations));
    }
}
