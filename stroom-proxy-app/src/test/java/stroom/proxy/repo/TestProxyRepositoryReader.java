package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.MetaMap;
import stroom.proxy.handler.MockRequestHandler;
import stroom.proxy.handler.RequestHandler;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomExpectedException;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipException;

public class TestProxyRepositoryReader extends StroomUnitTest {
    private ProxyRepositoryManager proxyRepositoryManager;
    private MockRequestHandler mockRequestHandler;
    private ProxyRepositoryReader proxyRepositoryReader;

    private void init() throws IOException {
        proxyRepositoryManager = new ProxyRepositoryManager();
        proxyRepositoryManager.setRepoDir(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")));

        mockRequestHandler = new MockRequestHandler();
        proxyRepositoryReader = new ProxyRepositoryReader(new stroom.util.task.MonitorImpl(), proxyRepositoryManager) {
            @Override
            public List<RequestHandler> createOutgoingRequestHandlerList() {
                final List<RequestHandler> list = new ArrayList<>();
                list.add(mockRequestHandler);
                return list;
            }

        };
    }

    @Test
    public void testSimpleNothingTodo() throws IOException {
        init();
        proxyRepositoryReader.doRunWork();
    }

    @Test
    public void testSimpleOneFile() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStream stroomZipOutputStream = proxyRepository.getStroomZipOutputStream();

        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_META_ENTRY,
                "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_DATA_ENTRY,
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(1, proxyRepository.getFileCount());
        stroomZipOutputStream.close();

        Assert.assertTrue(isZipFile(proxyRepository, 1));

        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(2, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleErrorCount());
        Assert.assertEquals(2, mockRequestHandler.getEntryNameList().size());

        final String sendHeader1 = new String(mockRequestHandler.getByteArray("001.meta"), StreamUtil.DEFAULT_CHARSET);
        Assert.assertTrue(sendHeader1.contains("Feed:TEST"));
        Assert.assertTrue(sendHeader1.contains("GUID:Z1"));

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));

    }

    private boolean isZipFile(final StroomZipRepository proxyRepository, final long num) {
        return isFile(proxyRepository, num, StroomZipRepository.ZIP_EXTENSION);
    }

    private boolean isBadFile(final StroomZipRepository proxyRepository, final long num) {
        return isFile(proxyRepository, num, StroomZipRepository.BAD_EXTENSION);
    }

    private boolean isFile(final StroomZipRepository proxyRepository, final long num, final String extension) {
        final AtomicBoolean found = new AtomicBoolean();
        final String idString = StroomFileNameUtil.idToString(num);

        try (final Stream<Path> stream = proxyRepository.walk(extension)) {
            final Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext() && !found.get()) {
                final Path p = iterator.next();
                final String fileName = p.getFileName().toString();
                if (fileName.startsWith(idString) && fileName.endsWith(extension) && !Character.isDigit(fileName.charAt(idString.length()))) {
                    found.set(true);
                }
            }
        } catch (final IOException e) {
            // Ignore.
        }

        return found.get();
    }

    @Test
    @StroomExpectedException(exception = IOException.class)
    public void testSimpleOneFileWithHeaderError() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStream stroomZipOutputStream = proxyRepository.getStroomZipOutputStream();

        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_META_ENTRY,
                "GUID:Z1\nFeed:TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_DATA_ENTRY,
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream.close();

        mockRequestHandler.setGenerateExceptionOnHeader(true);

        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleErrorCount());

        proxyRepository.clean();

        Assert.assertTrue("Expecting file to still be there", isZipFile(proxyRepository, 1));

    }

    @Test
    @StroomExpectedException(exception = IOException.class)
    public void testSimpleOneFileWithDataError() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStream stroomZipOutputStream = proxyRepository.getStroomZipOutputStream();

        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_META_ENTRY,
                "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_DATA_ENTRY,
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream.close();

        mockRequestHandler.setGenerateExceptionOnData(true);

        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleErrorCount());

        proxyRepository.clean();

        Assert.assertTrue("Expecting file to still be there", isZipFile(proxyRepository, 1));
    }

    @Test
    @StroomExpectedException(exception = {IOException.class, ZipException.class})
    public void testSimpleOneFileWithBadZip() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStreamImpl stroomZipOutputStream = (StroomZipOutputStreamImpl) proxyRepository.getStroomZipOutputStream();

        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_META_ENTRY,
                "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_DATA_ENTRY,
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream.close();

        final Path zipFile = stroomZipOutputStream.getFile();

        Files.delete(zipFile);

        OutputStream testStream = null;
        try {
            testStream = Files.newOutputStream(zipFile);
            testStream.write("not a zip file".getBytes(StreamUtil.DEFAULT_CHARSET));
        } finally {
            CloseableUtil.close(testStream);
        }

        proxyRepositoryReader.doRunWork();

        // As the zip is corrupt we can't even read the header
        Assert.assertEquals(0, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleErrorCount());

        proxyRepository.clean();

        Assert.assertTrue("Expecting bad file", isBadFile(proxyRepository, 1));
    }

    @Test
    public void testMultipleFilesWithContextSameFeed() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStream stroomZipOutputStream1 = proxyRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                new StroomZipEntry("req.header", "req", StroomZipFileType.Meta),
                "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                new StroomZipEntry("req.data", "req", StroomZipFileType.Data),
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                new StroomZipEntry("req.context", "req", StroomZipFileType.Context),
                "Test Context".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream1.close();

        final StroomZipOutputStream stroomZipOutputStream2 = proxyRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream2,
                new StroomZipEntry("req.header", "req", StroomZipFileType.Meta),
                "Feed:TEST\nGUID:Z2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream2,
                new StroomZipEntry("req.data", "req", StroomZipFileType.Data),
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream2,
                new StroomZipEntry("req.context", "req", StroomZipFileType.Context),
                "Test Context".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream2.close();

        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(6, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleErrorCount());

        Assert.assertEquals(Arrays.asList("001.meta", "001.ctx", "001.dat", "002.meta", "002.ctx", "002.dat"),
                mockRequestHandler.getEntryNameList());

        proxyRepository.clean();

        final String sendHeader2 = new String(mockRequestHandler.getByteArray("002.meta"), StreamUtil.DEFAULT_CHARSET);
        Assert.assertTrue(sendHeader2.contains("Feed:TEST"));
        Assert.assertTrue(sendHeader2.contains("GUID:Z2"));

        Assert.assertEquals("Test Data",
                new String(mockRequestHandler.getByteArray("001.dat"), StreamUtil.DEFAULT_CHARSET));
        Assert.assertEquals("Test Data",
                new String(mockRequestHandler.getByteArray("002.dat"), StreamUtil.DEFAULT_CHARSET));

        Assert.assertFalse(isZipFile(proxyRepository, 1));
        Assert.assertFalse(isZipFile(proxyRepository, 2));
    }

    @Test
    public void testMultipleFilesAtLimitWithContextSameFeed() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStream stroomZipOutputStream1 = proxyRepository.getStroomZipOutputStream();
        for (int i = 0; i < 10; i++) {
            StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                    new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Meta),
                    "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                    new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Data),
                    "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
            StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                    new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Context),
                    "Test Context".getBytes(StreamUtil.DEFAULT_CHARSET));
        }
        stroomZipOutputStream1.close();

        final StroomZipOutputStream stroomZipOutputStream2 = proxyRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream2,
                new StroomZipEntry("req.header", "req", StroomZipFileType.Meta),
                "Feed:TEST\nGUID:Z2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream2,
                new StroomZipEntry("req.data", "req", StroomZipFileType.Data),
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream2,
                new StroomZipEntry("req.context", "req", StroomZipFileType.Context),
                "Test Context".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream2.close();

        final StroomZipOutputStream stroomZipOutputStream3 = proxyRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream3,
                new StroomZipEntry("req.header", "req", StroomZipFileType.Meta),
                "Feed:TEST\nGUID:Z2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream3,
                new StroomZipEntry("req.data", "req", StroomZipFileType.Data),
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream3,
                new StroomZipEntry("req.context", "req", StroomZipFileType.Context),
                "Test Context".getBytes(StreamUtil.DEFAULT_CHARSET));
        stroomZipOutputStream3.close();

        proxyRepositoryReader.setMaxAggregation(10);
        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(2, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(2, mockRequestHandler.getHandleFooterCount());

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));
        Assert.assertFalse(isZipFile(proxyRepository, 2));
    }

    @Test
    public void testMultipleFilesAtSizeLimitSameFeed() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        for (int a = 0; a < 5; a++) {
            final StroomZipOutputStream stroomZipOutputStream1 = proxyRepository.getStroomZipOutputStream();
            for (int i = 0; i < 10; i++) {
                StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                        new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Meta),
                        "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                        new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Data),
                        "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
            stroomZipOutputStream1.close();
        }

        proxyRepositoryReader.setMaxAggregation(10000);
        proxyRepositoryReader.setMaxStreamSize(10L);
        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(5, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(5, mockRequestHandler.getHandleFooterCount());

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));
        Assert.assertFalse(isZipFile(proxyRepository, 2));
    }

    @Test
    public void testMultipleFilesAtSizeLimitSameFeed2() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        for (int a = 0; a < 5; a++) {
            final StroomZipOutputStream stroomZipOutputStream1 = proxyRepository.getStroomZipOutputStream();
            for (int i = 0; i < 10; i++) {
                StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                        new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Meta),
                        "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                        new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Data),
                        "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
            stroomZipOutputStream1.close();
        }

        proxyRepositoryReader.setMaxAggregation(10000);
        proxyRepositoryReader.setMaxStreamSize(100000L);
        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleFooterCount());

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));
        Assert.assertFalse(isZipFile(proxyRepository, 2));
    }

    @Test
    public void testMultipleFilesAtCountLimitSameFeed() throws IOException {
        init();

        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        for (int a = 0; a < 5; a++) {
            final StroomZipOutputStream stroomZipOutputStream1 = proxyRepository.getStroomZipOutputStream();
            for (int i = 0; i < 10; i++) {
                StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                        new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Meta),
                        "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
                StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream1,
                        new StroomZipEntry(null, String.valueOf(i), StroomZipFileType.Data),
                        "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
            stroomZipOutputStream1.close();
        }

        proxyRepositoryReader.setMaxAggregation(20);
        proxyRepositoryReader.setMaxStreamSize(null);
        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(3, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(3, mockRequestHandler.getHandleFooterCount());

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));
        Assert.assertFalse(isZipFile(proxyRepository, 2));
    }

    @Test
    public void testSimpleOneFileTemplated() throws IOException {
        init();

        MetaMap metaMap = new MetaMap();
        metaMap.put("feed", "myFeed");
        metaMap.put("key1", "myKey1");
        metaMap.put("key2", "myKey2");
        metaMap.put("key3", "myKey3");

        // template should be case insensitive as far as key names go as the metamap is case insensitive
        final String repositoryFormat = "${id}_${FEED}_${key2}_${kEy1}_${keyNotInMeta}_${Key3}";
        proxyRepositoryManager.setRepositoryFormat(repositoryFormat);
        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();

        final StroomZipOutputStream stroomZipOutputStream = proxyRepository.getStroomZipOutputStream(metaMap);

        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_META_ENTRY,
                "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_DATA_ENTRY,
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(1, proxyRepository.getFileCount());
        stroomZipOutputStream.close();

        String filename = null;
        try (final Stream<Path> stream = proxyRepository.walkZipFiles()) {
            final Iterator<Path> iter = stream.iterator();
            if (iter.hasNext()) {
                filename = iter.next().getFileName().toString();
            }
        }

        Assert.assertEquals("001_myFeed_myKey2_myKey1___myKey3.zip", filename);

        Assert.assertTrue(isZipFile(proxyRepository, 1));

        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(2, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleErrorCount());
        Assert.assertEquals(2, mockRequestHandler.getEntryNameList().size());

        final String sendHeader1 = new String(mockRequestHandler.getByteArray("001.meta"), StreamUtil.DEFAULT_CHARSET);
        Assert.assertTrue(sendHeader1.contains("Feed:TEST"));
        Assert.assertTrue(sendHeader1.contains("GUID:Z1"));

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));
    }

    @Test
    public void testSimpleOneFileEmptyTemplate() throws IOException {
        init();

        MetaMap metaMap = new MetaMap();
        metaMap.put("feed", "myFeed");
        metaMap.put("key1", "myKey1");
        metaMap.put("key2", "myKey2");
        metaMap.put("key3", "myKey3");

        //template should be case insensitive as far as key names go as the metamap is case insensitive
        final String repositoryFormat = "";
        proxyRepositoryManager.setRepositoryFormat(repositoryFormat);
        final StroomZipRepository proxyRepository = proxyRepositoryManager.getActiveRepository();
        final StroomZipOutputStream stroomZipOutputStream = proxyRepository.getStroomZipOutputStream(metaMap);

        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_META_ENTRY,
                "Feed:TEST\nGUID:Z1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        StroomZipOutputStreamUtil.addSimpleEntry(stroomZipOutputStream, StroomZipFile.SINGLE_DATA_ENTRY,
                "Test Data".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(1, proxyRepository.getFileCount());
        stroomZipOutputStream.close();

        String filename = null;
        try (final Stream<Path> stream = proxyRepository.walkZipFiles()) {
            final Iterator<Path> iter = stream.iterator();
            if (iter.hasNext()) {
                filename = iter.next().getFileName().toString();
            }
        }

        Assert.assertEquals("001.zip", filename);

        Assert.assertTrue(isZipFile(proxyRepository, 1));

        proxyRepositoryReader.doRunWork();

        Assert.assertEquals(1, mockRequestHandler.getHandleHeaderCount());
        Assert.assertEquals(1, mockRequestHandler.getHandleFooterCount());
        Assert.assertEquals(2, mockRequestHandler.getHandleEntryCount());
        Assert.assertEquals(0, mockRequestHandler.getHandleErrorCount());
        Assert.assertEquals(2, mockRequestHandler.getEntryNameList().size());

        final String sendHeader1 = new String(mockRequestHandler.getByteArray("001.meta"), StreamUtil.DEFAULT_CHARSET);
        Assert.assertTrue(sendHeader1.contains("Feed:TEST"));
        Assert.assertTrue(sendHeader1.contains("GUID:Z1"));

        proxyRepository.clean();

        Assert.assertFalse(isZipFile(proxyRepository, 1));
    }
}