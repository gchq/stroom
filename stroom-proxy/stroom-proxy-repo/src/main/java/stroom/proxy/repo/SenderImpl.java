package stroom.proxy.repo;

import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import javax.inject.Inject;

public class SenderImpl implements Sender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SenderImpl.class);

    private final ProgressLog progressLog;
    private final Path repoDir;

    @Inject
    SenderImpl(final ProgressLog progressLog,
               final RepoDirProvider repoDirProvider) {
        this.progressLog = progressLog;
        this.repoDir = repoDirProvider.get();
    }

    @Override
    public void sendDataToHandler(final Map<RepoSource, List<RepoSourceItem>> items,
                                  final StreamHandler handler) {
        String targetName;
        long sequenceId = 1;

        for (final Entry<RepoSource, List<RepoSourceItem>> mapEntry : items.entrySet()) {
            final RepoSource source = mapEntry.getKey();
            final List<RepoSourceItem> repoSourceItems = mapEntry.getValue();

            // Send no more if told to finish
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info(() -> "processFeedFiles() - Quitting early as we have been told to stop");
                throw new RuntimeException(
                        "processFeedFiles() - Quitting early as we have been told to stop");
            }

            final String sourcePath = source.getSourcePath();
            final Path zipFilePath = repoDir.resolve(sourcePath);
            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipFilePath))) {
                for (final RepoSourceItem item : repoSourceItems) {
                    targetName = StroomFileNameUtil.getIdPath(sequenceId++);

                    for (final RepoSourceEntry entry : item.getEntries()) {
                        final String sourceName = item.getName();
                        final String extension = entry.getExtension();
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
        final Path zipFilePath = repoDir.resolve(source.getSourcePath());
        final Consumer<Long> progressHandler = new ProgressHandler("Sending" +
                zipFilePath);
        processZipFile(zipFilePath, handler, progressHandler);
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
