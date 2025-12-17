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
import stroom.svg.shared.SvgImage;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.PrivilegedExceptionAction;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Creates an output stream for a file on an Hadoop Distributed File System
 * <p>
 * TODO Need to add in proper security so it can connect to a secured
 * (kerberos?) cluster
 */
@ConfigurableElement(
        type = "HDFSFileAppender",
        description = """
                A destination used to write an output stream to a file on a Hadoop Distributed File System.
                If multiple paths are specified in the 'outputPaths' property it will pick one at random.
                """,
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_HADOOP)
public class HDFSFileAppender extends AbstractAppender {

    private static final Logger LOGGER = LoggerFactory.getLogger(HDFSFileAppender.class);

    private static final String LOCK_EXTENSION = ".lock";

    private final PathCreator pathCreator;

    private String[] outputPaths;
    private String hdfsUri;
    private String runAsUser;

    private Configuration conf;
    private UserGroupInformation userGroupInformation;

    @Inject
    HDFSFileAppender(final ErrorReceiverProxy errorReceiverProxy,
                     final PathCreator pathCreator) {
        super(errorReceiverProxy);
        this.pathCreator = pathCreator;
    }

    public static Configuration buildConfiguration(final String hdfsUri) {
        final Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsUri);
        conf.set("fs.automatic.close", "true");
        conf.set("fs.hdfs.impl", DistributedFileSystem.class.getName());
        // conf.set("fs.file.impl",
        // org.apache.hadoop.fs.LocalFileSystem.class.getName());
        return conf;
    }

    public static FileSystem getHDFS(final Configuration conf) {
        final FileSystem hdfs;

        try {
            // Will return a cached instance keyed on the passed conf object
            hdfs = FileSystem.get(conf);
        } catch (final IOException e) {
            final String msg = "Error getting HDFS FileSystem object for " + conf.get("fs.defaultFS");
            LOGGER.error(msg, e);
            throw new RuntimeException(msg, e);
        }
        return hdfs;

    }

    public static UserGroupInformation buildRemoteUser(final Optional<String> runAsUser) {
        final String user = runAsUser.orElseGet(() -> {
            try {
                return UserGroupInformation.getCurrentUser().getUserName();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // userGroupInformation =
        // UserGroupInformation.createProxyUser(runAsUser,
        // UserGroupInformation.getLoginUser());
        return UserGroupInformation.createRemoteUser(user);

    }

    public static void runOnHDFS(final UserGroupInformation userGroupInformation, final Configuration conf,
                                 final Consumer<FileSystem> func) {
        try {
            userGroupInformation.doAs(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() {
                    final FileSystem hdfs = getHDFS(conf);

                    // run the passed lambda
                    func.accept(hdfs);

                    return null;
                }
            });
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void startProcessing() {
        if (hdfsUri == null) {
            throw new RuntimeException("No URI has been supplied for the Hadoop Distributed File System");
        }
        // conf = new Configuration();

        super.startProcessing();
    }

    @Override
    protected Output createOutput() throws IOException {
        return new BasicOutput(createHDFSLockedOutputStream());
    }

    HDFSLockedOutputStream createHDFSLockedOutputStream() throws IOException {
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
            final Path file = createCleanPath(path);

            return getHDFSLockedOutputStream(file);

        } catch (final IOException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * HDFS does not support some chars in its dir/filenames so need to clean
     * them here
     *
     * @param path The path to clean
     * @return A cleaned path
     */
    private Path createCleanPath(final String path) {
        return new Path(path.replaceAll(":", "-"));
    }

    private Configuration getConfiguration() {
        if (conf == null) {
            conf = buildConfiguration(hdfsUri);
        }
        return conf;
    }

    private FileSystem getHDFS() {
        final Configuration conf = getConfiguration();
        return getHDFS(conf);
    }

    private UserGroupInformation getUserGroupInformation() throws IOException {
        if (userGroupInformation == null) {
            userGroupInformation = buildRemoteUser(Optional.ofNullable(runAsUser));
        }

        return userGroupInformation;
    }

    private HDFSLockedOutputStream getHDFSLockedOutputStream(final Path filePath) throws IOException {
        final UserGroupInformation ugi = getUserGroupInformation();
        HDFSLockedOutputStream hdfsLockedOutputStream = null;

        try {
            hdfsLockedOutputStream = ugi.doAs(new PrivilegedExceptionAction<HDFSLockedOutputStream>() {
                @Override
                public HDFSLockedOutputStream run() {
                    try {
                        final FileSystem hdfs = getHDFS();
                        final Path dir = filePath.getParent();

                        // Create the directory if it doesn't exist
                        if (!hdfs.exists(dir)) {
                            hdfs.mkdirs(dir);
                        }

                        final Path lockFile = createCleanPath(filePath + LOCK_EXTENSION);
                        final Path outFile = filePath;

                        // Make sure we can create both output files without
                        // overwriting
                        // another
                        // file.
                        if (hdfs.exists(lockFile)) {
                            throw ProcessException.create("Output file \"" + lockFile.toString() + "\" already exists");
                        }

                        if (hdfs.exists(outFile)) {
                            throw ProcessException.create("Output file \"" + outFile.toString() + "\" already exists");
                        }

                        // Get a writer for the new lock file.
                        final OutputStream outputStream = new BufferedOutputStream(hdfs.create(lockFile));

                        return new HDFSLockedOutputStream(outputStream, lockFile, outFile, hdfs);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }

        if (hdfsLockedOutputStream == null) {
            throw new RuntimeException(String.format(
                    "Something went wrong creating the HDFSLockedOutputStream, outFile %s, hdfs uri %s",
                    filePath, hdfsUri));
        }

        return hdfsLockedOutputStream;
    }

    /**
     * @param outputPaths the outputPaths to set
     */
    @PipelineProperty(
            description = "One or more destination paths for output files separated with commas. Replacement " +
                          "variables can be used in path strings such as ${feed}.",
            displayPriority = 1)
    public void setOutputPaths(final String outputPaths) {
        this.outputPaths = outputPaths.split(",");
    }

    @PipelineProperty(description = "URI for the Hadoop Distributed File System (HDFS) to connect to, e.g. " +
                                    "hdfs://mynamenode.mydomain.com:8020",
            displayPriority = 2)
    public void setFileSystemUri(final String hdfsUri) {
        this.hdfsUri = hdfsUri;
    }

    @PipelineProperty(description = "The user to connect to HDFS as",
            displayPriority = 3)
    public void setRunAsUser(final String runAsUser) {
        this.runAsUser = runAsUser;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(description = "When the current output file exceeds this size it will be closed " +
                                    "and a new one created.",
            displayPriority = 4)
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }

    @PipelineProperty(description = "Choose if you want to split aggregated streams into separate output files.",
            defaultValue = "false",
            displayPriority = 5)
    public void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        super.setSplitAggregatedStreams(splitAggregatedStreams);
    }

    @PipelineProperty(description = "Choose if you want to split individual records into separate output files.",
            defaultValue = "false",
            displayPriority = 6)
    public void setSplitRecords(final boolean splitRecords) {
        super.setSplitRecords(splitRecords);
    }

    /**
     * Used for injecting a configuration in testing
     */
    void setConf(final Configuration conf) {
        this.conf = conf;
    }
}
