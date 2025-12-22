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

import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

public abstract class FileSystemTestUtil {

    // These zips are downloaded by the gradle task downloadStroomContent
    // Ought to be the same as in ContentPackDownloader
    private static final Path CONTENT_PACK_DOWNLOADS_DIR = Paths.get(
            System.getProperty("user.home"),
            "/.stroom/contentPackDownload");
    private static final Path EXPLODED_DIR = CONTENT_PACK_DOWNLOADS_DIR.resolve("exploded");

    private static final long TEST_PREFIX = System.currentTimeMillis();
    private static final AtomicLong TEST_SUFFIX = new AtomicLong();

    private FileSystemTestUtil() {
        // Utility
    }

    /**
     * @return a unique string for testing
     */
    public static String getUniqueTestString() {
        return TEST_PREFIX + "_" + TEST_SUFFIX.incrementAndGet();
    }

    /**
     * @return The directory where content pack zips are downloaded to.
     * It will ensure it exists.
     */
    public static Path getContentPackDownloadsDir() {
        try {
            Files.createDirectories(CONTENT_PACK_DOWNLOADS_DIR);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error ensuring directory {}",
                    CONTENT_PACK_DOWNLOADS_DIR.toAbsolutePath()), e);
        }
        return CONTENT_PACK_DOWNLOADS_DIR;
    }

    /**
     * @return The directory where content pack zips are unzipped to.
     * It will ensure it exists.
     */
    public static Path getExplodedContentPacksDir() {
        try {
            Files.createDirectories(EXPLODED_DIR);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error ensuring directory {}",
                    EXPLODED_DIR.toAbsolutePath()), e);
        }
        return EXPLODED_DIR;
    }

//    /**
//     * @return The directory of the unziped content pack. If it has not already
//     * been unziped then it will unzip the pack first, which is assumed to exist.
//     */
//    public static Path getExplodedContentPackDir(final ContentPackZip contentPack) {
//
//        final Path explodedPackDir = getExplodedContentPacksDir()
//                .resolve(contentPack.toString());
//
//        if (!Files.exists(explodedPackDir)) {
//            final Path downloadsDir = getContentPackDownloadsDir();
//            final Path packZip = downloadsDir.resolve(contentPack.toFileName());
//
//            if (!Files.exists(packZip)) {
//                ContentPackZipDownloader.downloadContentPack(contentPack, downloadsDir);
//            }
//
//            // Unzip the zip file.
//            try {
//                ZipUtil.unzip(packZip, explodedPackDir);
//            } catch (IOException e) {
//                throw new RuntimeException(LogUtil.message("Error unzipping {} into {}",
//                        packZip.toAbsolutePath().normalize(),
//                        explodedPackDir.toAbsolutePath().normalize()));
//            }
//        }
//        return explodedPackDir;
//    }
}
