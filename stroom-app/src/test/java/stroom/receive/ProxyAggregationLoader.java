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

package stroom.receive;

import stroom.data.zip.StroomZipEntry;
import stroom.util.io.FileUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Class for creating test files in the proxy aggregation directory for manually
 * testing proxy aggregation
 */
public class ProxyAggregationLoader {

    private static void writeTestFile(final Path testFile, final String feedName, final String data)
            throws IOException {

        FileUtil.mkdirs(testFile.getParent());
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(Files.newOutputStream(testFile))) {
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(StroomZipEntry.SINGLE_META_ENTRY.getFullName()));
            PrintWriter printWriter = new PrintWriter(zipOutputStream);
            printWriter.println("Feed:" + feedName);
            printWriter.println("Proxy:ProxyTest");
            printWriter.flush();
            zipOutputStream.closeArchiveEntry();
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(StroomZipEntry.SINGLE_DATA_ENTRY.getFullName()));
            printWriter = new PrintWriter(zipOutputStream);
            printWriter.print(data);
            printWriter.flush();
            zipOutputStream.closeArchiveEntry();
        }
    }

    //main method for manually testing proxy aggregation with a running stroom instance
    public static void main(final String[] args) throws IOException, InterruptedException {

        final Path proxyDir = Paths.get("/home/dev/tmp/dev/proxy");
//        final File proxyDir = new File("/tmp/stroom/dev/proxy");

        FileUtil.mkdirs(proxyDir);

        for (int i = 1; i <= 1000000000; i++) {
            final Path testFile1 = proxyDir.resolve(String.format("%08d", i) + ".zip");
            final int feedNo = (i % 4) + 1;
            writeTestFile(testFile1, "TEST_FEED_" + feedNo, i + "-data1\n" + i + "-data1\n");
            Thread.sleep(200);
        }
    }
}
