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

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaDataHolder;
import stroom.svg.shared.SvgImage;
import stroom.util.io.CompressionUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Joins text instances into a single text instance.
 */
@ConfigurableElement(
        type = "FileAppender",
        description = """
                A destination used to write an output stream to a file on the file system.
                If multiple paths are specified in the 'outputPaths' property it will pick one at random to \
                write to.
                """,
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_FILE)
public class FileAppender extends AbstractAppender {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAppender.class);

    private static final String LOCK_EXTENSION = ".lock";
    private static final String DEFAULT_USE_COMPRESSION_PROP_VALUE = "false";
    private static final String DEFAULT_COMPRESSION_METHOD_PROP_VALUE = CompressorStreamFactory.GZIP;

    private final PathCreator pathCreator;
    private final OutputFactory outputFactory;
    private String[] outputPaths;
    private String filePermissions;

    @Inject
    public FileAppender(final ErrorReceiverProxy errorReceiverProxy,
                        final MetaDataHolder metaDataHolder,
                        final PathCreator pathCreator) {
        super(errorReceiverProxy);
        this.pathCreator = pathCreator;
        outputFactory = new OutputFactory(metaDataHolder);

        // Ensure outputStreamSupport has the defaults for FileAppender
        //noinspection ConstantValue
        setUseCompression(Boolean.parseBoolean(DEFAULT_USE_COMPRESSION_PROP_VALUE));
        setCompressionMethod(DEFAULT_COMPRESSION_METHOD_PROP_VALUE);
    }

    @Override
    protected Output createOutput() throws IOException {
        try {
            if (outputPaths == null || outputPaths.length == 0) {
                throw new IOException("No output paths have been set");
            }

            // Get a path to use.
            String path;
            if (outputPaths.length == 1) {
                path = outputPaths[0];
            } else {
                // Choose one of the output paths at random.
                path = outputPaths[(int) Math.round(Math.random() * (outputPaths.length - 1))];
            }

            // Replace some of the path elements with system variables.
            path = pathCreator.replaceAll(path);

            // Make sure we can create this path.
            final Path file = Paths.get(path);
            final Path dir = file.getParent();
            final Set<PosixFilePermission> permissions = parsePosixFilePermissions(filePermissions);
            if (!Files.isDirectory(dir)) {
                try {
                    Files.createDirectories(dir);

                    // Set permissions on the created directory
                    if (permissions != null) {
                        Files.setPosixFilePermissions(dir, permissions);
                    }
                } catch (final IOException e) {
                    throw ProcessException.create("Unable to create output dirs: " + FileUtil.getCanonicalPath(dir));
                }
            }

            final Path lockFile = Paths.get(path + LOCK_EXTENSION);

            // Make sure we can create both output files without overwriting
            // another file.
            if (Files.exists(lockFile)) {
                throw ProcessException.create(
                        "Output file \"" + FileUtil.getCanonicalPath(lockFile) + "\" already exists");
            }
            if (Files.exists(file)) {
                throw ProcessException.create(
                        "Output file \"" + FileUtil.getCanonicalPath(file) + "\" already exists");
            }
            LOGGER.trace("Creating output stream for path {}", path);

            // Get a writer for the new lock file.
            final Output output = outputFactory
                    .create(new BufferedOutputStream(Files.newOutputStream(lockFile)));

            return new LockedOutput(output, lockFile, file, permissions);

        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
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

    /**
     * @param outputPaths the outputPaths to set
     */
    @PipelineProperty(
            description = "One or more destination paths for output files separated with commas. " +
                          "Replacement variables can be used in path strings such as ${feed}.",
            displayPriority = 1)
    public void setOutputPaths(final String outputPaths) {
        this.outputPaths = outputPaths.split(",");
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "When the current output file exceeds this size it will be closed and a new one created.",
            displayPriority = 2)
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }

    @PipelineProperty(
            description = "Choose if you want to split aggregated streams into separate output files.",
            defaultValue = "false",
            displayPriority = 3)
    public void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        super.setSplitAggregatedStreams(splitAggregatedStreams);
    }

    @PipelineProperty(
            description = "Choose if you want to split individual records into separate output files.",
            defaultValue = "false",
            displayPriority = 4)
    public void setSplitRecords(final boolean splitRecords) {
        super.setSplitRecords(splitRecords);
    }

    @PipelineProperty(
            description = "Apply compression to output files.",
            defaultValue = DEFAULT_USE_COMPRESSION_PROP_VALUE,
            displayPriority = 5)
    public void setUseCompression(final boolean useCompression) {
        outputFactory.setUseCompression(useCompression);
    }

    @PipelineProperty(
            description = "Compression method to apply, if compression is enabled. Supported values: " +
                          CompressionUtil.SUPPORTED_COMPRESSORS + ".",
            defaultValue = DEFAULT_COMPRESSION_METHOD_PROP_VALUE,
            displayPriority = 6)
    public void setCompressionMethod(final String compressionMethod) {
        try {
            outputFactory.setCompressionMethod(compressionMethod);
        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
            throw e;
        }
    }

    @PipelineProperty(description = "Set file system permissions of finished files (example: 'rwxr--r--')",
            displayPriority = 8)
    public void setFilePermissions(final String filePermissions) {
        this.filePermissions = filePermissions;
    }
}
