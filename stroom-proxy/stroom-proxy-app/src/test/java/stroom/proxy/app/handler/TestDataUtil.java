package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestDataUtil {
    public static Path writeZip(final FeedKey... feedKeys) throws IOException {
        final Path tempDir = Files.createTempDirectory("temp");
        final FileGroup fileGroup = new FileGroup(tempDir);
        writeZip(fileGroup, 1, feedKeys);
        return fileGroup.getZip();
    }

    public static void writeZip(final FileGroup fileGroup,
                                final int entryCount,
                                final FeedKey... feedKeys) throws IOException {
        final byte[] buffer = LocalByteBuffer.get();
        try (final Writer entryWriter = Files.newBufferedWriter(fileGroup.getEntries())) {
            try (final ZipWriter zipWriter = new ZipWriter(fileGroup.getZip(), buffer)) {
                int count = 1;
                for (int i = 0; i < entryCount; i++) {
                    for (final FeedKey feedKey : feedKeys) {
                        final String baseName = NumericFileNameUtil.create(count++);
                        final AttributeMap attributeMap = new AttributeMap();
                        AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
                        final byte[] metaBytes;
                        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            AttributeMapUtil.write(attributeMap, baos);
                            baos.flush();
                            metaBytes = baos.toByteArray();
                        }

                        final String metaEntryName = baseName + StroomZipFileType.META.getDotExtension();
                        zipWriter.writeStream(metaEntryName, new ByteArrayInputStream(metaBytes));

                        final byte[] dataBytes = "test".getBytes(StandardCharsets.UTF_8);
                        final String dataEntryName = baseName + StroomZipFileType.DATA.getDotExtension();
                        zipWriter.writeStream(dataEntryName, new ByteArrayInputStream(dataBytes));

                        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey.feed(), feedKey.type());
                        zipEntryGroup.setMetaEntry(new Entry(metaEntryName, metaBytes.length));
                        zipEntryGroup.setDataEntry(new Entry(dataEntryName, dataBytes.length));

                        zipEntryGroup.write(entryWriter);
                    }
                }
            }
        }
    }

    public static void writeFileGroup(final FileGroup fileGroup,
                                      final int entryCount,
                                      final FeedKey feedKey) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
        AttributeMapUtil.write(attributeMap, fileGroup.getMeta());
        writeZip(fileGroup, entryCount, feedKey);
    }
}
