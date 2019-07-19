package stroom.proxy.repo;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.shared.Monitor;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

public class TestProxyRepositoryReader extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyRepositoryReader.class);

    private static final String SOME_DATA = "SOME_DATA";
    //This is needed as you can't have to RunWith annotations
    //so this is the same as @RunWith(MockitoJUnitRunner.class)
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ProxyRepositoryReaderConfig mockProxyRepositoryReaderConfig;
    @Mock
    private ProxyRepositoryStreamHandlerFactory mockHandlerFactory;
    @Mock
    private ProxyRepositoryStreamHandler mockHandler;
    @Mock
    private Monitor mockMonitor;

    @Before
    public void setUp() throws Exception {
        Mockito.when(mockProxyRepositoryReaderConfig.getForwardThreadCount())
                .thenReturn(1);
        Mockito.when(mockProxyRepositoryReaderConfig.getMaxFileScan())
                .thenReturn(1);
        Mockito.when(mockProxyRepositoryReaderConfig.getMaxConcurrentMappedFiles())
                .thenReturn(1);
        Mockito.when(mockProxyRepositoryReaderConfig.getMaxAggregation())
                .thenReturn(1);
        Mockito.when(mockProxyRepositoryReaderConfig.getMaxStreamSize())
                .thenReturn(1L);

        Mockito.when(mockHandlerFactory.addSendHandlers(Mockito.anyList()))
                .thenReturn(Collections.singletonList(mockHandler));
    }


    public void doRunWork_nonRolling() throws IOException {

        final Path repoDir = FileUtil.getTempDir();
        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(
                repoDir, null, null);

        String info = "Feed:TEST_FEED\n" +
                "Proxy:ProxyTest\n" +
                "StreamSize:" + SOME_DATA.getBytes().length + "\n" +
                "ReceivedTime:2010-01-01T00:00:00.000Z";

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                    SOME_DATA.getBytes(CharsetConstants.DEFAULT_CHARSET));
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Meta),
                    info.getBytes(CharsetConstants.DEFAULT_CHARSET));
        }

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Meta),
                    info.getBytes(CharsetConstants.DEFAULT_CHARSET));
        }

        final ProxyRepositoryReader proxyRepositoryReader = new ProxyRepositoryReader(
                mockMonitor,
                proxyRepositoryManager,
                mockProxyRepositoryReaderConfig,
                mockHandlerFactory);

        Assertions.assertThat(proxyRepositoryManager.getReadableRepository())
                .hasSize(1);
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository().get(0).listAllZipFiles())
                .hasSize(2);

        proxyRepositoryReader.doRunWork();

        Assertions.assertThat(proxyRepositoryManager.getReadableRepository().get(0).listAllZipFiles())
                .hasSize(0);
        Assertions.assertThat(repoDir)
                .exists();
        Assertions.assertThat(Files.list(repoDir).count())
                .isEqualTo(0);
    }

    @Test
    public void doRunWork_rolling() throws IOException {

        final Scheduler scheduler = new Scheduler() {
            @Override
            public boolean execute() {
                // Always run
                return true;
            }

            @Override
            public Long getScheduleReferenceTime() {
                return null;
            }
        };

        final Path repoDir = FileUtil.getTempDir();
        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(
                repoDir, null, scheduler);

        String info = "Feed:TEST_FEED\n" +
                "Proxy:ProxyTest\n" +
                "StreamSize:" + SOME_DATA.getBytes().length + "\n" +
                "ReceivedTime:2010-01-01T00:00:00.000Z";

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                    SOME_DATA.getBytes(CharsetConstants.DEFAULT_CHARSET));
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Meta),
                    info.getBytes(CharsetConstants.DEFAULT_CHARSET));
        }
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository())
                .hasSize(0);

        proxyRepositoryManager.doRunWork();

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                    "SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            StroomZipOutputStreamUtil.addSimpleEntry(stream, new StroomZipEntry(null, "file", StroomZipFileType.Meta),
                    info.getBytes(CharsetConstants.DEFAULT_CHARSET));
        }
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository())
                .hasSize(1);

        proxyRepositoryManager.doRunWork();

        final ProxyRepositoryReader proxyRepositoryReader = new ProxyRepositoryReader(
                mockMonitor,
                proxyRepositoryManager,
                mockProxyRepositoryReaderConfig,
                mockHandlerFactory);

        Assertions.assertThat(proxyRepositoryManager.getReadableRepository())
                .hasSize(2);
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository().get(0).listAllZipFiles())
                .hasSize(1);
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository().get(1).listAllZipFiles())
                .hasSize(1);


        // process all files in repo
        proxyRepositoryReader.doRunWork();

        Assertions.assertThat(proxyRepositoryManager.getReadableRepository())
                .hasSize(2);
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository().get(0).listAllZipFiles())
                .hasSize(0);
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository().get(1).listAllZipFiles())
                .hasSize(0);
        Assertions.assertThat(repoDir)
                .exists();
        Assertions.assertThat(Files.list(repoDir).count())
                .isEqualTo(2);

        // this is a bit of a hack to make all dirs in the repo appear older.
        // this is to prevent the code from skipping dirs as they are too new
        Files.walkFileTree(repoDir, new AbstractFileVisitor() {
            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                try {
                    FileTime newCreateTime = FileTime.from(Instant.now().minus(10, ChronoUnit.MINUTES));
                    Files.setLastModifiedTime(dir, newCreateTime);
                    final BasicFileAttributes attr = Files.readAttributes(dir, BasicFileAttributes.class);
                    final FileTime readCreateTime = attr.creationTime();
                    LOGGER.info("Setting new modified time of directory {} to {}", dir, readCreateTime);
                    Assertions.assertThat(readCreateTime)
                            .isEqualTo(newCreateTime);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // now run again so clean is called
        proxyRepositoryReader.doRunWork();

        // rolled repos have gone but root dir remains
        Assertions.assertThat(proxyRepositoryManager.getReadableRepository())
                .hasSize(0);
        Assertions.assertThat(repoDir)
                .exists();
        Assertions.assertThat(Files.list(repoDir).count())
                .isEqualTo(0);
    }
}