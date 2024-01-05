package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.FeedKey;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
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

    @Test
    void testSimple() throws IOException {
        final int inputZipCount = 10;
        final int entryCountPerZip = 1;
        test(entryCountPerZip, inputZipCount);
    }

    @Test
    void testSimple2() throws IOException {
        final int inputZipCount = 1;
        final int entryCountPerZip = 3;
        test(entryCountPerZip, inputZipCount);
    }

    @Disabled
    @Test
    void testPerformance() throws IOException {
        final int inputZipCount = 100;
        final int entryCountPerZip = 100;
        test(entryCountPerZip, inputZipCount);
    }

    private void test(final int entryCountPerZip,
                      final int inputZipCount) throws IOException {
        final Path tempDir = Files.createTempDirectory("temp");
        final Path dataDir = Files.createTempDirectory("repo");
        final TempDirProvider tempDirProvider = () -> tempDir;
        final DataDirProvider dataDirProvider = () -> dataDir;
        final CleanupDirQueue cleanupDirQueue = new CleanupDirQueue(dataDirProvider);
        final AtomicInteger aggregateCount = new AtomicInteger();
        final Aggregator aggregator = new Aggregator(
                cleanupDirQueue,
                tempDirProvider);
        aggregator.setDestination(aggregatorDir -> {
            try {
                aggregateCount.getAndIncrement();
                final FileGroup fileGroup = new FileGroup(aggregatorDir);
                final List<String> actualList = ZipUtil.pathList(fileGroup.getZip());
                final List<String> expectedList = createExpectedList(entryCountPerZip * inputZipCount);
                assertThat(actualList).isEqualTo(expectedList);

                // In normal use the supplied dir would be moved ready for new a new aggregate to be created so simulate
                // by deleting it.
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
            TestDataUtil.writeFileGroup(fileGroup, entryCountPerZip, new FeedKey("test-feed", "test-type"));
        }

        aggregator.addDir(path);
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
