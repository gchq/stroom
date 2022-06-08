package stroom.proxy.repo;

import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
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
    public void sendDataToHandler(final Items items,
                                  final StreamHandler handler) {
        String targetName;
        long sequenceId = 1;

        final List<Items.Source> sources = items
                .map()
                .keySet()
                .stream()
                .sorted(Comparator.comparing(Items.Source::id))
                .toList();
        for (final Items.Source source : sources) {
            // Send no more if told to finish
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info(() -> "processFeedFiles() - Quitting early as we have been told to stop");
                throw new RuntimeException(
                        "processFeedFiles() - Quitting early as we have been told to stop");
            }

            final FileSet fileSet = sequentialFileStore.getStoreFileSet(source.fileStoreId());
            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fileSet.getZip()))) {
                final List<Items.Item> repoSourceItems = items
                        .map()
                        .get(source)
                        .stream()
                        .sorted(Comparator.comparing(Items.Item::id))
                        .toList();
                for (final Items.Item item : repoSourceItems) {
                    targetName = StroomFileNameUtil.getIdPath(sequenceId++);

                    final String extensions = item.extensions();
                    final List<String> extensionList = Arrays
                            .stream(extensions.split(","))
                            .sorted(Comparator.comparingInt(entry -> StroomZipFileType.fromExtension(entry).getId()))
                            .toList();

                    for (final String extension : extensionList) {
                        final String sourceName = item.name();
                        final String fullSourceName = sourceName + extension;
                        final String fullTargetName = targetName + extension;

                        final Consumer<Long> progressHandler = new ProgressHandler("Sending" +
                                fullTargetName);

                        final ZipArchiveEntry zipArchiveEntry = zipFile.getEntry(fullSourceName);
                        try (final ByteCountInputStream inputStream =
                                new ByteCountInputStream(zipFile.getInputStream(zipArchiveEntry))) {
                            LOGGER.debug(() -> "sendEntry() - " + fullTargetName);

                            handler.addEntry(targetName + extension, inputStream, progressHandler);
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
                    progressLog.increment("AggregateForwarder - forwardAggregateItem");
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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

                    // Add meta data.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.META, handler, progressHandler);

                    // Add context data.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.CONTEXT, handler, progressHandler);

                    // Add data.
                    addEntry(stroomZipFile, baseName, StroomZipFileType.DATA, handler, progressHandler);
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
