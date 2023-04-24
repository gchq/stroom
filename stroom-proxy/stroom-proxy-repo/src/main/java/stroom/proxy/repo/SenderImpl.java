package stroom.proxy.repo;

import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StreamHandler;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class SenderImpl implements Sender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SenderImpl.class);

    private final ProgressLog progressLog;
    private final SequentialFileStore sequentialFileStore;

    @Inject
    SenderImpl(final ProgressLog progressLog,
               final SequentialFileStore sequentialFileStore) {
        this.progressLog = progressLog;
        this.sequentialFileStore = sequentialFileStore;
    }

    @Override
    public void sendDataToHandler(final AttributeMap attributeMap,
                                  final List<SourceItems> items,
                                  final StreamHandler handler) {
        String targetName;
        long sequenceId = 0;

        for (final SourceItems sourceItems : items) {
            final SourceItems.Source source = sourceItems.source();

            // Send no more if told to finish
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info(() -> "processFeedFiles() - Quitting early as we have been told to stop");
                throw new RuntimeException(
                        "processFeedFiles() - Quitting early as we have been told to stop");
            }

            final FileSet fileSet = sequentialFileStore.getStoreFileSet(source.fileStoreId());
            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fileSet.getZip()))) {
                final List<SourceItems.Item> repoSourceItems = sourceItems.list();
                for (final SourceItems.Item item : repoSourceItems) {
                    sequenceId++;
                    targetName = StroomFileNameUtil.getIdPath(sequenceId);

                    // Add attributes as a manifest to the output.
                    if (sequenceId == 1) {
                        final StringWriter stringWriter = new StringWriter();
                        AttributeMapUtil.write(attributeMap, stringWriter);
                        final InputStream inputStream = new ByteArrayInputStream(
                                stringWriter.toString().getBytes(StandardCharsets.UTF_8));
                        final String fullTargetName = targetName + StroomZipFileType.MANIFEST.getExtension();
                        final Consumer<Long> progressHandler = new ProgressHandler("Sending" +
                                fullTargetName);
                        handler.addEntry(fullTargetName, inputStream, progressHandler);
                    }

                    // Figure out if we have meta and/or context extensions.
                    final String[] extensions = item.extensions().split(",");
                    String metaExtension = null;
                    String contextExtension = null;
                    for (final String extension : extensions) {
                        final StroomZipFileType stroomZipFileType =
                                StroomZipFileType.fromExtension(extension);
                        if (StroomZipFileType.META.equals(stroomZipFileType)) {
                            metaExtension = extension;
                        } else if (StroomZipFileType.CONTEXT.equals(stroomZipFileType)) {
                            contextExtension = extension;
                        }
                    }

                    // Send all file data with data extensions and add meta and context if present.
                    for (final String extension : extensions) {
                        final StroomZipFileType stroomZipFileType =
                                StroomZipFileType.fromExtension(extension);
                        if (StroomZipFileType.DATA.equals(stroomZipFileType)) {
                            final String sourceName = item.name();

                            // Add meta if it exists.
                            if (metaExtension != null) {
                                final String fullMetaSourceName =
                                        sourceName + metaExtension;
                                final String fullMetaTargetName =
                                        targetName + StroomZipFileType.META.getExtension();

                                sendEntry(zipFile, fullMetaSourceName, fullMetaTargetName, handler);
                            }

                            // Add context if it exists.
                            if (contextExtension != null) {
                                final String fullContextSourceName =
                                        sourceName + contextExtension;
                                final String fullContextTargetName =
                                        targetName + StroomZipFileType.CONTEXT.getExtension();

                                sendEntry(zipFile, fullContextSourceName, fullContextTargetName, handler);
                            }

                            // Add the data.
                            final String fullSourceName =
                                    sourceName + extension;
                            final String fullTargetName =
                                    targetName + StroomZipFileType.DATA.getExtension();

                            sendEntry(zipFile, fullSourceName, fullTargetName, handler);
                        }
                    }
                    progressLog.increment("AggregateForwarder - forwardAggregateItem");
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void sendEntry(final ZipFile zipFile,
                           final String fullSourceName,
                           final String fullTargetName,
                           final StreamHandler handler) throws IOException {
        final Consumer<Long> progressHandler = new ProgressHandler("Sending" +
                fullTargetName);

        final ZipArchiveEntry zipArchiveEntry = zipFile.getEntry(fullSourceName);
        try (final ByteCountInputStream inputStream =
                new ByteCountInputStream(zipFile.getInputStream(zipArchiveEntry))) {
            LOGGER.debug(() -> "sendEntry() - " + fullTargetName);

            handler.addEntry(fullTargetName, inputStream, progressHandler);
            final long totalRead = inputStream.getCount();

            LOGGER.trace(() -> "sendEntry() - " +
                    fullTargetName +
                    " " +
                    ModelStringUtil.formatIECByteSizeString(
                            totalRead));

            if (totalRead == 0) {
                LOGGER.warn(() -> "sendEntry() - " + fullTargetName + " IS BLANK");
            }
            LOGGER.debug(() -> "sendEntry() - " + fullTargetName + " size is " + totalRead);
        }

        progressLog.increment("AggregateForwarder - forwardAggregateEntry");
    }

    public void sendDataToHandler(final RepoSource source,
                                  final StreamHandler handler) {
        final FileSet fileSet = sequentialFileStore.getStoreFileSet(source.fileStoreId());
        final Consumer<Long> progressHandler = new ProgressHandler("Sending" +
                fileSet.getZip());
        processZipFile(fileSet.getZip(), handler, progressHandler);
    }

    public void processZipFile(final Path zipFilePath,
                               final StreamHandler handler,
                               final Consumer<Long> progressHandler) {
        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFilePath)) {
            try {
                for (final String baseName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
                    // Add manifest.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.MANIFEST, handler, progressHandler);

                    // Add data.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.DATA, handler, progressHandler);

                    // Add meta data.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.META, handler, progressHandler);

                    // Add context data.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.CONTEXT, handler, progressHandler);
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addEntry(final StroomZipFile stroomZipFile,
                          final String baseName,
                          final StroomZipFileType stroomZipFileType,
                          final StreamHandler handler,
                          final Consumer<Long> progressHandler) throws IOException {
        try (final InputStream inputStream =
                stroomZipFile.getInputStream(baseName, stroomZipFileType)) {
            if (inputStream != null) {
                handler.addEntry(baseName + stroomZipFileType.getExtension(), inputStream, progressHandler);
            }
        }
    }
}
