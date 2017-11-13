package stroom.streamtask.server;

import stroom.util.io.FileUtil;
import stroom.util.thread.ThreadUtil;
import stroom.util.zip.StroomZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Class for creating test files in the proxy aggregation directory for manually
 * testing proxy aggregation
 */
public class ProxyAggregationLoader {

    private static void writeTestFile(final File testFile, final String feedName, final String data)
            throws IOException {

        FileUtil.mkdirs(testFile.getParentFile());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(testFile));
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

//        final File proxyDir = new File("/home/dev/tmp/dev/proxy");
        final File proxyDir = new File("/tmp/stroom/dev/proxy");

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 1000000000; i++) {
            final File testFile1 = new File(proxyDir, String.format("%08d", i) + ".zip");
            int feedNo = (i % 2) + 1;
            writeTestFile(testFile1, "TEST_FEED_" + feedNo, i + "-data1\n" + i + "-data1\n");
            ThreadUtil.sleep(250);
        }
    }
}
