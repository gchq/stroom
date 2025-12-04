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

package stroom.test.common.docs;

import stroom.test.common.ProjectPathUtil;
import stroom.util.io.DiffUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Utils to help with generating docs content for the stroom-docs repo from stroom code.
 */
public class StroomDocsUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomDocsUtil.class);

    private static final String STROOM_PACKAGE_NAME = "stroom";

    // Set this prop if stroom-docs is not in a dir called 'stroom-docs' and is not in
    // a dir that is a sibling of this stroom repo.
    public static final String STROOM_DOCS_REPO_DIR_PROP_KEY = "stroom.docs.repo.dir";

    private static final String MARKER_START_HTML = "<!-- #~#~#~#~#~# GENERATED CONTENT START #~#~#~#~#~#~# -->";
    private static final String MARKER_END_HTML = "<!-- #~#~#~#~#~# GENERATED CONTENT END #~#~#~#~#~#~# -->";

    private StroomDocsUtil() {
    }

    public static boolean replaceGeneratedContent(final Path file,
                                                  final String generatedContent) {
        return replaceGeneratedContent(
                file,
                generatedContent,
                MARKER_START_HTML,
                MARKER_END_HTML);
    }

    public static boolean replaceGeneratedContent(final Path file,
                                                  final String generatedContent,
                                                  final String startMarker,
                                                  final String endMarker) {
        if (NullSafe.isBlankString(generatedContent)) {
            throw new RuntimeException("content is blank");
        }
        checkFileExists(file);
        checkFileIsWritable(file);

        try {
            final String existingFileContent = Files.readString(file);
            final List<String> headerLines = new ArrayList<>();
            final List<String> footerLines = new ArrayList<>();
            // Add the existing non-generated content
            final AtomicBoolean seenStartMarker = new AtomicBoolean(false);
            final AtomicBoolean seenEndMarker = new AtomicBoolean(false);

            // Markers are assumed to take a whole line, so anything else on that line is
            // ignored if it contains a marker
            existingFileContent.lines()
                    .forEach(line -> {
                        if (NullSafe.contains(line, startMarker)) {
                            seenStartMarker.set(true);
                        } else if (NullSafe.contains(line, endMarker)) {
                            if (!seenStartMarker.get()) {
                                throw new RuntimeException(LogUtil.message(
                                        "Seen end marker '{}' before start marker '{}'",
                                        endMarker,
                                        startMarker));
                            }
                            seenEndMarker.set(true);
                        } else {
                            if (!seenStartMarker.get()) {
                                headerLines.add(line);
                            } else if (seenEndMarker.get()) {
                                footerLines.add(line);
                            }
                        }
                    });

            if (!seenStartMarker.get()) {
                throw new RuntimeException(LogUtil.message("Did not find start marker '{}'", startMarker));
            }

            // Add the marker line
            final List<String> modifiedLines = new ArrayList<>(headerLines);
            modifiedLines.add(MARKER_START_HTML);

            // Now add in the new generated content
            generatedContent.lines().forEach(modifiedLines::add);

            // Not all files need an end marker
            if (seenEndMarker.get()) {
                modifiedLines.add(MARKER_END_HTML);
                modifiedLines.addAll(footerLines);
            }

            final String newFileContent = String.join("\n", modifiedLines);

            final boolean contentDiffers = DiffUtil.unifiedDiff(
                    existingFileContent,
                    newFileContent,
                    true,
                    3,
                    diffLines -> LOGGER.debug(() -> LogUtil.message(
                            "Showing modifications for file {}\n{}",
                            file, String.join("\n", diffLines))));

            if (contentDiffers) {
                Files.writeString(
                        file,
                        newFileContent,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);

                return true;
            } else {
                LOGGER.debug("File content not changed for file {}", file);
                return false;
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error reading file '{}': {}",
                    file.toAbsolutePath().normalize(),
                    LogUtil.exceptionMessage(e)));
        }
    }

    private static void checkFileExists(final Path file) {
        if (!Files.isRegularFile(file)) {
            throw new RuntimeException(LogUtil.message("File '{}' does not exist",
                    file.toAbsolutePath().normalize()));
        }
    }

    private static void checkFileIsWritable(final Path file) {
        if (!Files.isWritable(file)) {
            throw new RuntimeException(LogUtil.message("File '{}' is not writable",
                    file.toAbsolutePath().normalize()));
        }
    }

    /**
     * @param subPath A path to a file in the stroom-docs repo that is relative to the
     *                stroom-docs repo root.
     * @return An absolute path to the file which has been tested to see if it exists and
     * is a regular file.
     */
    public static Path resolveStroomDocsFile(final Path subPath) {
        final String stroomDocsRepoDirStr = System.getProperty(STROOM_DOCS_REPO_DIR_PROP_KEY);
        final Path stroomDocsRepoDir;

        if (NullSafe.isBlankString(stroomDocsRepoDirStr)) {
            final Path stroomRepoRoot = ProjectPathUtil.getRepoRoot();
            stroomDocsRepoDir = stroomRepoRoot.getParent()
                    .resolve("stroom-docs");
        } else {
            stroomDocsRepoDir = Paths.get(stroomDocsRepoDirStr);
        }
        if (!Files.isDirectory(stroomDocsRepoDir)) {
            throw new RuntimeException(LogUtil.message(
                    "stroom-docs dir '{}' does not exist. " +
                    "Expecting it to be a sibling of this stroom repo. If that is not the case, either " +
                    "conform or set the system prop '{}' to the location of your stroom-docs repo.",
                    stroomDocsRepoDir.toAbsolutePath().normalize(),
                    STROOM_DOCS_REPO_DIR_PROP_KEY));
        }

        final Path file = stroomDocsRepoDir.resolve(subPath).toAbsolutePath().normalize();

        if (!Files.isRegularFile(file)) {
            throw new RuntimeException(LogUtil.message("stroom-docs file '{}' does not exist",
                    stroomDocsRepoDir.toAbsolutePath().normalize()));
        }
        return file;
    }

    public static void doWithClassScanResult(final Consumer<ScanResult> scanResultConsumer) {
        try (final ScanResult scanResult =
                new ClassGraph()
                        .enableAllInfo()             // Scan classes, methods, fields, annotations
                        .acceptPackages(STROOM_PACKAGE_NAME)  // Scan com.xyz and subpkgs (omit to scan all packages)
                        .scan()) {                   // Start the scan

            NullSafe.consume(scanResult, scanResultConsumer);
        }
    }


    // --------------------------------------------------------------------------------


    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface GeneratesDocumentation {

        // At the mo, just a marker anno to help find methods used to generated docs
    }
}
