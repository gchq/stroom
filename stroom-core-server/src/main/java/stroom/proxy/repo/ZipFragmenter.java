/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

class ZipFragmenter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipFragmenter.class);

    private final ErrorReceiver errorReceiver;

    ZipFragmenter(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    public void fragment(final Path path, final BasicFileAttributes attrs) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Getting zip info for  '" + FileUtil.getCanonicalPath(path) + "'");
        }

        // Create output dir.
        final String fileName = path.getFileName().toString();
        final int index = fileName.lastIndexOf(".");
        if (index != -1) {
            final String stem = fileName.substring(0, index);
            final Path outputDir = path.getParent().resolve(stem + PathConstants.PARTS);

            if (!Files.isDirectory(outputDir)) {
                try {
                    Files.createDirectory(outputDir);
                } catch (final IOException e) {
                    errorReceiver.onError(
                            path,
                            "Unable to create directory '" + FileUtil.getCanonicalPath(outputDir) + "'");
                }
            }

            Path currentDir = outputDir;
            if (Files.isDirectory(outputDir)) {
                int i = 1;
                boolean success = false;

                try (final StroomZipFile stroomZipFile = new StroomZipFile(path)) {
                    final Set<String> baseNameSet = stroomZipFile.getStroomZipNameSet().getBaseNameSet();

                    if (baseNameSet.isEmpty()) {
                        errorReceiver.onError(path, "Unable to find any entry?");
                    } else {
                        for (final String baseName : baseNameSet) {
                            final String idString = StroomFileNameUtil.idToString(i);
                            final String subPath = StroomFileNameUtil.idToPathId(idString);
                            if (subPath.length() > 0) {
                                final Path dir = outputDir.resolve(subPath);
                                // Create sub directories if we have some and haven't tried before.
                                if (!dir.equals(currentDir)) {
                                    Files.createDirectories(dir);
                                    currentDir = dir;
                                }
                            }

                            final Path outputFile = currentDir.resolve(stem + PathConstants.PART + idString + ".zip");
                            // If output file already exists then it ought to be overwritten automatically.
                            try (final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStreamImpl(outputFile)) {
                                transferEntry(stroomZipFile, stroomZipOutputStream, baseName, StroomZipFileType.Meta);
                                transferEntry(stroomZipFile, stroomZipOutputStream, baseName, StroomZipFileType.Context);
                                transferEntry(stroomZipFile, stroomZipOutputStream, baseName, StroomZipFileType.Data);
                            }
                            i++;
                        }

                        success = true;
                    }
                } catch (final IOException | RuntimeException e) {
                    // Unable to open file ... must be bad.
                    errorReceiver.onError(path, e.getMessage());
                    LOGGER.error(e.getMessage(), e);
                }

                if (success) {
                    // Delete the original file.
                    FileUtil.delete(path);
                }
            }
        }
    }

    private void transferEntry(
            final StroomZipFile input,
            final StroomZipOutputStream output,
            final String baseName,
            final StroomZipFileType type) {
        try (final InputStream inputStream = input.getInputStream(baseName, type)) {
            if (inputStream != null) {
                final String outputEntryName = new StroomZipEntry(null, baseName, type).getFullName();
                try (final OutputStream outputStream = output.addEntry(outputEntryName)) {
                    StreamUtil.streamToStream(inputStream, outputStream, false);
                } catch (final IOException e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            }
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }
}
