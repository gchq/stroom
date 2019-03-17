package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipFileType;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

class ZipInfoExtractor {
    private final Logger LOGGER = LoggerFactory.getLogger(ZipInfoExtractor.class);

    private final ErrorReceiver errorReceiver;

    ZipInfoExtractor(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    public ZipInfo extract(final Path path, final BasicFileAttributes attrs) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Getting zip info for  '" + FileUtil.getCanonicalPath(path) + "'");
        }

        MetaMap metaMap = null;
        long totalUncompressedSize = 0;
        int zipEntryCount = 0;

        try (final StroomZipFile stroomZipFile = new StroomZipFile(path)) {
            final Set<String> baseNameSet = stroomZipFile.getStroomZipNameSet().getBaseNameSet();
            zipEntryCount = baseNameSet.size();

            if (baseNameSet.isEmpty()) {
                errorReceiver.onError(path, "Unable to find any entry?");
            } else {
                for (final String sourceName : baseNameSet) {
                    // Extract meta data
                    if (metaMap == null) {
                        try {
                            final InputStream metaStream = stroomZipFile.getInputStream(sourceName, StroomZipFileType.Meta);
                            if (metaStream == null) {
                                errorReceiver.onError(path, "Unable to find meta?");
                            } else {
                                metaMap = new MetaMap();
                                metaMap.read(metaStream, false);
                            }
                        } catch (final RuntimeException e) {
                            errorReceiver.onError(path, e.getMessage());
                            LOGGER.error(e.getMessage(), e);
                        }
                    }

                    totalUncompressedSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Meta);
                    totalUncompressedSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Context);
                    totalUncompressedSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Data);
                }
            }
        } catch (final IOException | RuntimeException e) {
            // Unable to open file ... must be bad.
            errorReceiver.onError(path, e.getMessage());
            LOGGER.error(e.getMessage(), e);
        }

        // Get compressed size.
        Long totalCompressedSize = null;
        try {
            totalCompressedSize = Files.size(path);
        } catch (final IOException | RuntimeException e) {
            errorReceiver.onError(path, e.getMessage());
            LOGGER.error(e.getMessage(), e);
        }

        String feedName = null;

        if (metaMap == null) {
            errorReceiver.onError(path, "Unable to find meta data");
        } else {
            feedName = metaMap.get(StroomHeaderArguments.FEED);
            if (feedName == null || feedName.length() == 0) {
                errorReceiver.onError(path, "Unable to find feed in header??");
            }
        }

        final ZipInfo zipInfo = new ZipInfo(path,
                feedName,
                totalUncompressedSize,
                totalCompressedSize,
                attrs.lastModifiedTime().toMillis(),
                zipEntryCount);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Zip info for  '" + FileUtil.getCanonicalPath(path) + "' is " + zipInfo);
        }

        return zipInfo;
    }

    private long getRawEntrySize(final StroomZipFile stroomZipFile,
                                 final String sourceName,
                                 final StroomZipFileType fileType)
            throws IOException {
        final long size = stroomZipFile.getSize(sourceName, fileType);
        if (size == -1) {
            throw new IOException("Unknown raw file size");
        }

        return size;
    }
}
