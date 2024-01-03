package stroom.proxy.app.handler;

import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class TestZipEntryGroup extends StroomUnitTest {

    private static final int ENTRIES = 100;

    @Test
    void test() throws IOException {
        final String data;

        // Write data
        try (final StringWriter writer = new StringWriter()) {
            for (int i = 0; i < ENTRIES; i++) {
                final ZipEntryGroup zipEntryGroup = new ZipEntryGroup("test_feed", "test_type");
                zipEntryGroup.setManifestEntry(new Entry(i + ".mf", 123));
                zipEntryGroup.setMetaEntry(new Entry(i + ".meta", 234));
                zipEntryGroup.setContextEntry(new Entry(i + ".ctx", 345));
                zipEntryGroup.setDataEntry(new Entry(i + ".dat", 456));
                ZipEntryGroupUtil.writeLine(writer, zipEntryGroup);
            }
            writer.flush();
            data = writer.toString();
        }

        // Read data
        try (final BufferedReader bufferedReader = new BufferedReader(new StringReader(data))) {
            String line = bufferedReader.readLine();
            int i = 0;
            while (line != null) {
                final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.readLine(line);
                assertThat(zipEntryGroup.getFeedName()).isEqualTo("test_feed");
                assertThat(zipEntryGroup.getTypeName()).isEqualTo("test_type");
                assertThat(zipEntryGroup.getManifestEntry().getName()).isEqualTo(i + ".mf");
                assertThat(zipEntryGroup.getManifestEntry().getUncompressedSize()).isEqualTo(123);
                assertThat(zipEntryGroup.getMetaEntry().getName()).isEqualTo(i + ".meta");
                assertThat(zipEntryGroup.getMetaEntry().getUncompressedSize()).isEqualTo(234);
                assertThat(zipEntryGroup.getContextEntry().getName()).isEqualTo(i + ".ctx");
                assertThat(zipEntryGroup.getContextEntry().getUncompressedSize()).isEqualTo(345);
                assertThat(zipEntryGroup.getDataEntry().getName()).isEqualTo(i + ".dat");
                assertThat(zipEntryGroup.getDataEntry().getUncompressedSize()).isEqualTo(456);

                i++;
                line = bufferedReader.readLine();
            }

            assertThat(i).isEqualTo(ENTRIES);
        }
    }
}
