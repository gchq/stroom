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

package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.FeedKey;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestAggregator extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAggregator.class);

    private static final FeedKey FEED_KEY = FeedKey.of("test-feed", "test-type");

    @Test
    void testSimple() throws IOException {
        final int inputZipCount = 10;
        final int entryCountPerZip = 1;
        test(entryCountPerZip, inputZipCount, 2);
    }

    @Test
    void testSimple2() throws IOException {
        final int inputZipCount = 1;
        final int entryCountPerZip = 3;
        test(entryCountPerZip, inputZipCount, 2);
    }

    @Disabled
    @Test
    void testPerformance() throws IOException {
        final int inputZipCount = 100;
        final int entryCountPerZip = 100;
        test(entryCountPerZip, inputZipCount, 500);
    }

    private void test(final int entryCountPerZip,
                      final int inputZipCount,
                      final int dataLines) throws IOException {
        final Path dataDir = Files.createTempDirectory("repo");
        final DataDirProvider dataDirProvider = () -> dataDir;
        final CleanupDirQueue cleanupDirQueue = new CleanupDirQueue(dataDirProvider);
        final AtomicInteger aggregateCount = new AtomicInteger();
        final Aggregator aggregator = new Aggregator(
                cleanupDirQueue,
                dataDirProvider);
        aggregator.setDestination(aggregatorDir -> {
            try {
                aggregateCount.getAndIncrement();
                final FileGroup fileGroup = new FileGroup(aggregatorDir);
                final List<String> actualList = ZipUtil.pathList(fileGroup.getZip());
                final List<String> expectedList = createExpectedList(entryCountPerZip * inputZipCount);
                assertThat(actualList)
                        .isEqualTo(expectedList);

                // In normal use the supplied dir would be moved ready for new a new
                // aggregate to be created so simulate by deleting it.
                FileUtil.deleteDir(aggregatorDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Create a dir of zip files to merge.
        final Path path = Files.createTempDirectory("temp");
        final NumberedDirProvider numberedDirProvider = new NumberedDirProvider(path);
        for (int i = 0; i < inputZipCount; i++) {
            final Path dir = numberedDirProvider.get();
            final FileGroup fileGroup = new FileGroup(dir);
            TestDataUtil.writeFileGroup(fileGroup, dataLines, entryCountPerZip, FEED_KEY);
        }

        LOGGER.logDurationIfDebugEnabled(() -> {
            aggregator.addDir(path);
        }, "addDir");

        assertThat(aggregateCount.get()).isEqualTo(1);
    }

    private List<String> createExpectedList(final int entryCount) {
        final List<String> expectedList = new ArrayList<>();
        for (int i = 1; i <= entryCount; i++) {
            final String baseName = NumericFileNameUtil.create(i);
            expectedList.add(baseName + StroomZipFileType.META.getDotExtension());
            expectedList.add(baseName + StroomZipFileType.DATA.getDotExtension());
        }
        return expectedList;
    }
}
