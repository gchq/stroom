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

package stroom.pipeline.writer;

import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.destination.RollingFileDestination;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;
import stroom.util.io.CompressionUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Set;

/**
 * Joins text instances into a single text instance.
 */
@ConfigurableElement(
        type = "RollingFileAppender",
        description = """
                A destination used to write an output stream to a file on the file system.
                If multiple paths are specified in the 'outputPaths' property it will pick one at random to \
                write to.
                This is distinct from the FileAppender in that when the `rollSize` is reached it will move the \
                current file to the path specified in `rolledFileName` and resume writing to the original path.
                This allows other processes to follow the changes to a single file path, e.g. when using `tail`.
                On system shutdown all active files will be rolled.
                """,
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_FILES)
public class RollingFileAppender extends AbstractRollingAppender {

    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileAppender.class);

    private final PathCreator pathCreator;

    private String[] outputPaths;
    private String fileNamePattern;
    private String rolledFileNamePattern;
    private boolean useCompression;
    private String compressionMethod = CompressorStreamFactory.GZIP;
    private String filePermissions;
    private String dir;
    private String fileName;
    private String rolledFileName;
    private String key;

    @Inject
    RollingFileAppender(final RollingDestinations destinations,
                        final PathCreator pathCreator) {
        super(destinations);
        this.pathCreator = pathCreator;
    }

    @Override
    public RollingDestination createDestination() throws IOException {
        String dir = this.dir;
        String fileName = this.fileName;

        dir = pathCreator.replaceTimeVars(dir);
        dir = pathCreator.replaceUUIDVars(dir);

        fileName = pathCreator.replaceTimeVars(fileName);
        fileName = pathCreator.replaceUUIDVars(fileName);

        // Create a new destination.
        final Path file = Paths.get(dir).resolve(fileName);

        // Try and create the path.
        final Path parentDir = file.getParent();
        final Set<PosixFilePermission> permissions = parsePosixFilePermissions(filePermissions);
        if (!Files.isDirectory(parentDir)) {
            try {
                Files.createDirectories(parentDir);

                // Set permissions on the created directory
                if (permissions != null) {
                    Files.setPosixFilePermissions(parentDir, permissions);
                }
            } catch (final IOException e) {
                throw ProcessException.create("Unable to create output dirs: " + FileUtil.getCanonicalPath(parentDir));
            }
        }

        return new RollingFileDestination(pathCreator,
                key,
                getFrequencyTrigger(),
                getCronTrigger(),
                getRollSize(),
                Instant.now(),
                fileName,
                rolledFileName,
                parentDir,
                file,
                useCompression,
                compressionMethod,
                permissions
        );
    }

    @Override
    protected Object getKey() throws IOException {
        try {
            // Create the current file name if one isn't set.
            if (key == null) {
                dir = getRandomOutputPath();
                dir = pathCreator.replaceContextVars(dir);
                dir = pathCreator.toAppPath(dir).toString();

                fileName = fileNamePattern;
                fileName = pathCreator.replaceContextVars(fileName);
                fileName = pathCreator.replaceSystemProperties(fileName);

                rolledFileName = rolledFileNamePattern;
                rolledFileName = pathCreator.replaceContextVars(rolledFileName);
                rolledFileName = pathCreator.replaceSystemProperties(rolledFileName);

                key = dir + '/' + fileName;
            }

            return key;
        } catch (final RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private String getRandomOutputPath() throws IOException {
        if (outputPaths == null || outputPaths.length == 0) {
            throw new IOException("No output paths have been set");
        }

        // Get a path to use.
        final String path;
        if (outputPaths.length == 1) {
            path = outputPaths[0];
        } else {
            // Choose one of the output paths at random.
            path = outputPaths[(int) Math.round(Math.random() * (outputPaths.length - 1))];
        }

        return path;
    }

    /**
     * Parses a POSIX-style file permission string like "rwxr--r--"
     */
    private static Set<PosixFilePermission> parsePosixFilePermissions(final String filePermissions) {
        if (filePermissions == null || filePermissions.isEmpty()) {
            return null;
        }

        try {
            return PosixFilePermissions.fromString(filePermissions);
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Invalid file permissions format: '" + filePermissions + "'");
            return null;
        }
    }

    @Override
    protected void validateSpecificSettings() {
        if (outputPaths == null || outputPaths.length == 0) {
            throw ProcessException.create("No output paths have been specified");
        }

        if (fileNamePattern == null || fileNamePattern.isEmpty()) {
            throw ProcessException.create("No file name has been specified");
        }

        if (rolledFileNamePattern == null || rolledFileNamePattern.isEmpty()) {
            throw ProcessException.create("No rolled file name has been specified");
        }

        if (fileNamePattern.equals(rolledFileNamePattern)) {
            throw ProcessException.create("File name and rolled file name cannot be the same");
        }
    }

    @PipelineProperty(
            description = "One or more destination paths for output files separated with commas. " +
                          "Replacement variables can be used in path strings such as ${feed}.",
            displayPriority = 1)
    public void setOutputPaths(final String outputPaths) {
        this.outputPaths = outputPaths.split(",");
    }

    @PipelineProperty(description = "Choose the name of the file to write.",
            displayPriority = 2)
    public void setFileName(final String fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    @PipelineProperty(description = "Choose the name that files will be renamed to when they are rolled.",
            displayPriority = 3)
    public void setRolledFileName(final String rolledFileNamePattern) {
        this.rolledFileNamePattern = rolledFileNamePattern;
    }

    @PipelineProperty(description = "Choose how frequently files are rolled.",
            defaultValue = "1h",
            displayPriority = 4)
    public void setFrequency(final String frequency) {
        super.setFrequency(frequency);
    }

    @PipelineProperty(description = "Provide a cron expression to determine when files are rolled.",
            displayPriority = 5)
    public void setSchedule(final String expression) {
        super.setSchedule(expression);
    }

    @PipelineProperty(
            description = "When the current output file exceeds this size it will be closed and a new one " +
                          "created, e.g. 10M, 1G.",
            defaultValue = "100M",
            displayPriority = 6)
    public void setRollSize(final String rollSize) {
        super.setRollSize(rollSize);
    }

    @PipelineProperty(
            description = "Apply GZIP compression to output files",
            defaultValue = "false",
            displayPriority = 7)
    public void setUseCompression(final boolean useCompression) {
        this.useCompression = useCompression;
    }

    @PipelineProperty(
            description = "Compression method to apply, if compression is enabled. Supported values: " +
                          CompressionUtil.SUPPORTED_COMPRESSORS + ".",
            defaultValue = CompressorStreamFactory.GZIP,
            displayPriority = 8)
    public void setCompressionMethod(final String compressionMethod) {
        if (!NullSafe.isBlankString(compressionMethod)) {
            if (CompressionUtil.isSupportedCompressor(compressionMethod)) {
                this.compressionMethod = compressionMethod;
            } else {
                final String errorMsg = "Unsupported compression method: " + compressionMethod;
                throw ProcessException.create(errorMsg);
            }
        }
    }

    @PipelineProperty(description = "Set file system permissions of finished files (example: 'rwxr--r--')",
            displayPriority = 10)
    public void setFilePermissions(final String filePermissions) {
        this.filePermissions = filePermissions;
    }
}
