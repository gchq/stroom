package stroom.streamtask.server;

import stroom.proxy.repo.StroomZipFile;
import stroom.util.io.FileUtil;
import stroom.util.thread.ThreadUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Class for creating test files in the proxy aggregation directory for manually
 * testing proxy aggregation
 */
public class ProxyAggregationLoader {

    private static void writeTestFile(final Path testFile, final String feedName, final String data)
            throws IOException {

        FileUtil.mkdirs(testFile.getParent());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(testFile));
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_META_ENTRY.getFullName()));
        PrintWriter printWriter = new PrintWriter(zipOutputStream);
        printWriter.println("Feed:" + feedName);
        printWriter.println("Proxy:ProxyTest");
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry(StroomZipFile.SINGLE_DATA_ENTRY.getFullName()));
        printWriter = new PrintWriter(zipOutputStream);
        printWriter.print(data);
        printWriter.flush();
        zipOutputStream.closeEntry();
        zipOutputStream.close();
    }

    //main method for manually testing proxy aggregation with a running stroom instance
    public static void main(final String[] args) throws IOException {

        final Path proxyDir = Paths.get("/home/dev/tmp/dev/proxy");
//        final File proxyDir = new File("/tmp/stroom/dev/proxy");

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 1000000000; i++) {
            final Path testFile1 = proxyDir.resolve(String.format("%08d", i) + ".zip");
            int feedNo = (i % 4) + 1;
            writeTestFile(testFile1, "TEST_FEED_" + feedNo, i + "-data1\n" + i + "-data1\n");
            ThreadUtil.sleep(200);
        }
    }
}
