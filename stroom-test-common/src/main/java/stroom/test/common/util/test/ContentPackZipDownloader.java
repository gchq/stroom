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

package stroom.test.common.util.test;

import stroom.content.ContentPack;
import stroom.content.GitRepo;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

public class ContentPackZipDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackZipDownloader.class);
    public static final String CONTENT_PACK_DOWNLOAD_DIR = "~/.stroom/contentPackDownload";

    private static void downloadZip(final String url,
                                    final Path file) throws IOException {
        try (final InputStream in = new URL(url).openStream()) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void downloadZip(final ContentPackZip contentPackZip,
                                    final Path contentPackDownloadDir,
                                    final Path contentPackImportDir,
                                    final HttpClient httpClient) {

        final Path downloadFile = buildDestFilePath(contentPackZip, contentPackDownloadDir);
        final Path importFile = buildDestFilePath(contentPackZip, contentPackImportDir);

        ensureDirectoryExists(contentPackImportDir);

        if (!Files.isRegularFile(importFile)) {

            // Do the download (if it is not there already)
            downloadContentPackZip(contentPackZip, contentPackDownloadDir, httpClient);

            LOGGER.info("Copying from " + downloadFile + " to " + importFile);
            try {
                StreamUtil.copyFile(downloadFile, importFile);
            } catch (final IOException e) {
                throw new RuntimeException(LogUtil.message("Error copying {} to {}: {}",
                        downloadFile, importFile, e.getMessage()), e);
            }
        } else {
            LOGGER.info("File {} already exists", importFile.toAbsolutePath().normalize());
        }
    }

    private static void ensureDirectoryExists(final Path contentPackDownloadDir) {
        try {
            Files.createDirectories(contentPackDownloadDir);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring {} exists: {}",
                    contentPackDownloadDir, e.getMessage()), e);
        }
    }

    public static synchronized void downloadZipPacks(final Path contentPacksDefinition,
                                                     final Path contentPackDownloadDir,
                                                     final Path contentPackImportDir,
                                                     final HttpClient httpClient) {
        LOGGER.info("Downloading content packs using definition {}, with download dir {} and import dir {}",
                contentPacksDefinition.toAbsolutePath(),
                contentPackDownloadDir.toAbsolutePath(),
                contentPackImportDir.toAbsolutePath());
        try {
            Files.createDirectories(contentPackDownloadDir);
        } catch (final IOException e) {
            LOGGER.error("Error ensuring {} exists: {}", contentPackDownloadDir.toAbsolutePath(), e.getMessage(), e);
        }

        try {
            Files.createDirectories(contentPackImportDir);
        } catch (final IOException e) {
            LOGGER.error("Error ensuring {} exists: {}", contentPackImportDir.toAbsolutePath(), e.getMessage(), e);
        }

        try {
            final ObjectMapper mapper = JsonUtil.getMapper();
            final ContentPackZipCollection contentPacks = mapper.readValue(
                    contentPacksDefinition.toFile(),
                    ContentPackZipCollection.class);

            contentPacks.getContentPacks().forEach(contentPack ->
                    downloadZip(contentPack, contentPackDownloadDir, contentPackImportDir, httpClient));
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static Path downloadContentPackZip(final ContentPackZip contentPackZip,
                                              final Path destDir,
                                              final HttpClient httpClient) {
        return downloadContentPackZip(contentPackZip, destDir, ConflictMode.KEEP_EXISTING, httpClient);
    }

    public static synchronized Path downloadContentPack(final ContentPack contentPack,
                                                        final Path destDir) {
        final GitRepo gitRepo = contentPack.getRepo();
        final Path dir = destDir
                .resolve(gitRepo.getName())
                .resolve(gitRepo.getBranch())
                .resolve(gitRepo.getCommit());

        gitPull(contentPack.getRepo(), dir);

        return dir;
    }

    public static synchronized Path gitPull(final GitRepo gitRepo,
                                            final Path destDir) {
        final Path lockFilePath = Path.of(destDir.toAbsolutePath() + ".lock");
        final Path completedFilePath = Path.of(destDir.toAbsolutePath() + ".complete");
        final Path parent = lockFilePath.getParent();
        try {
            Files.createDirectories(lockFilePath.getParent());
        } catch (final IOException e) {
            throw new RuntimeException("Error creating lockFilePath parent dir " + parent);
        }

        if (Files.exists(completedFilePath) && Files.exists(destDir)) {
            LOGGER.debug("{} already exists, nothing to do", destDir);
        } else {
            // Multiple test JVMs cannot interact with the git repo at once,
            // else git's locking will be violated, so easier for
            // all to lock on a single file and do it serially.
            final DurationTimer timer = DurationTimer.start();
            FileUtil.doUnderFileLock(lockFilePath, () -> {
                LOGGER.info("Acquired lock on {} in {}", lockFilePath, timer);

                // Now re-test for existence
                if (Files.exists(completedFilePath) && Files.exists(destDir)) {
                    LOGGER.debug("{} already exists, nothing to do", destDir);
                } else {
                    if (Files.exists(destDir)) {
                        LOGGER.info("Found incomplete git clone {}, deleting it", destDir);
                        FileUtil.deleteDir(destDir);
                    }
                    LOGGER.info("Pulling from Git repo: {} into destDir: {}", gitRepo, destDir);
                    try (final Git git = Git
                            .cloneRepository()
                            .setURI(gitRepo.getUri())
                            .setBranch(gitRepo.getBranch())
                            .setDirectory(destDir.toFile())
                            .call()) {
                        git.checkout().setName(gitRepo.getCommit()).call();
                    } catch (final GitAPIException e) {
                        LOGGER.error(e.getMessage(), e);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                    try {
                        // Create the .complete file so other jvms know the git clone is good to use
                        FileUtil.touch(completedFilePath);
                    } catch (final IOException e) {
                        throw new RuntimeException(LogUtil.message(
                                "Error creating file {} - {}", completedFilePath, LogUtil.exceptionMessage(e), e));
                    }
                }
            });
        }

        return destDir;
    }

    /**
     * synchronized to avoid multiple test threads downloading the same pack concurrently
     * Don't have to worry about other JVM as this method is called under lock on a single common file.
     */
    public static synchronized Path downloadContentPackZip(final ContentPackZip contentPackZip,
                                                           final Path destDir,
                                                           final ConflictMode conflictMode,
                                                           final HttpClient httpClient) {
        Preconditions.checkNotNull(contentPackZip);
        Preconditions.checkNotNull(destDir);
        Preconditions.checkNotNull(conflictMode);
        Preconditions.checkArgument(Files.isDirectory(destDir));

        final Path destFilePath = buildDestFilePath(contentPackZip, destDir);
        final Path lockFilePath = buildLockFilePath(contentPackZip, destDir);

        ensureDirectoryExists(destDir);

        FileUtil.doUnderFileLock(lockFilePath, () -> {
            // Now we have the lock for this zip file we can see if we need to download it or not

            boolean destFileExists = Files.isRegularFile(destFilePath);

            if (destFileExists && conflictMode.equals(ConflictMode.KEEP_EXISTING)) {
                LOGGER.debug("Requested contentPack {} already exists in {}, keeping existing",
                        contentPackZip.getName(),
                        FileUtil.getCanonicalPath(destFilePath));
            } else {
                if (destFileExists && conflictMode.equals(ConflictMode.OVERWRITE_EXISTING)) {
                    LOGGER.debug("Requested contentPack {} already exists in {}, overwriting existing",
                            contentPackZip.getName(),
                            FileUtil.getCanonicalPath(destFilePath));
                    try {
                        Files.delete(destFilePath);
                        destFileExists = false;
                    } catch (final IOException e) {
                        throw new UncheckedIOException(String.format("Unable to remove existing content pack %s",
                                FileUtil.getCanonicalPath(destFilePath)), e);
                    }
                }

                if (destFileExists) {
                    LOGGER.info("ContentPack {} already exists {}",
                            contentPackZip.getName(),
                            FileUtil.getCanonicalPath(destFilePath));
                } else {
                    final URL fileUrl = getUrl(contentPackZip);
                    LOGGER.info("Downloading contentPack {} from {} to {}",
                            contentPackZip.getName(),
                            fileUrl,
                            FileUtil.getCanonicalPath(destFilePath));

                    downloadFile(fileUrl, destFilePath, httpClient);
                }
            }
        });

        return destFilePath;
    }

    private static URL getUrl(final ContentPackZip contentPackZip) {
        try {
            return new URL(contentPackZip.getUrl());
        } catch (final MalformedURLException e) {
            throw new RuntimeException("Url " +
                                       contentPackZip.getUrl() +
                                       " for content pack " +
                                       contentPackZip.getName() +
                                       " and version " +
                                       contentPackZip.getVersion() +
                                       " is badly formed", e);
        }
    }

    static Path buildDestFilePath(final ContentPackZip contentPackZip, final Path destDir) {
        final String filename = contentPackZip.toFileName();
        return destDir.resolve(filename);
    }

    static Path buildLockFilePath(final ContentPackZip contentPackZip, final Path destDir) {
        final String filename = contentPackZip.toFileName();
        return destDir.resolve(filename + ".lock");
    }

    private static boolean isRedirected(final Map<String, List<String>> header) {
        for (final String hv : header.get(null)) {
            if (hv.contains(" 301 ")
                || hv.contains(" 302 ")) {
                return true;
            }
        }
        return false;
    }

    private static void downloadFile(final URL fileUrl,
                                     final Path destFilename,
                                     final HttpClient httpClient) {
        try {
            final HttpGet httpGet = new HttpGet(fileUrl.toString());
            httpClient.execute(httpGet, response -> {
                try (final InputStream inputStream = response.getEntity().getContent()) {
                    // Create a temp file as the download destination to avoid overwriting an existing file.
                    final Path tempFile = Files.createTempFile("stroom", "download");
                    try (final OutputStream fos = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                        StreamUtil.streamToStream(inputStream, fos);
                    }

                    // Atomically move the downloaded file to the destination so that
                    // concurrent tests don't overwrite the file.
                    try {
                        Files.move(tempFile, destFilename);
                    } catch (final FileAlreadyExistsException e) {
                        // Don't see why we should get here as the methods are synchronized
                        LOGGER.warn("Unable to move {} to {} as file already exists, ignoring the error.",
                                tempFile.toAbsolutePath().normalize(),
                                destFilename.toAbsolutePath().normalize(),
                                e);
                    }
                }
                return response.getCode();
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(String.format("Error downloading url %s to %s",
                    fileUrl.toString(), FileUtil.getCanonicalPath(destFilename)), e);
        }
    }

    public enum ConflictMode {
        OVERWRITE_EXISTING,
        KEEP_EXISTING
    }
}
