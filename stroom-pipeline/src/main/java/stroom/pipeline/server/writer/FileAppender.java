/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Joins text instances into a single text instance.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "FileAppender",
        category = Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.FILE)
public class FileAppender extends AbstractAppender {
    private static final String LOCK_EXTENSION = ".lock";

    private final PathCreator pathCreator;
    private ByteCountOutputStream byteCountOutputStream;
    private String[] outputPaths;

    @Inject
    public FileAppender(final ErrorReceiverProxy errorReceiverProxy,
                        final PathCreator pathCreator) {
        super(errorReceiverProxy);
        this.pathCreator = pathCreator;
    }

    @Override
    protected OutputStream createOutputStream() throws IOException {
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
            if (!Files.isDirectory(dir)) {
                try {
                    Files.createDirectories(dir);
                } catch (final IOException e) {
                    throw new ProcessException("Unable to create output dirs: " + FileUtil.getCanonicalPath(dir));
                }
            }

            final Path lockFile = Paths.get(path + LOCK_EXTENSION);
            final Path outFile = file;

            // Make sure we can create both output files without overwriting
            // another file.
            if (Files.exists(lockFile)) {
                throw new ProcessException("Output file \"" + FileUtil.getCanonicalPath(lockFile) + "\" already exists");
            }
            if (Files.exists(outFile)) {
                throw new ProcessException("Output file \"" + FileUtil.getCanonicalPath(outFile) + "\" already exists");
            }

            // Get a writer for the new lock file.
            byteCountOutputStream = new ByteCountOutputStream(new BufferedOutputStream(Files.newOutputStream(lockFile)));
            return new LockedOutputStream(byteCountOutputStream, lockFile, outFile);

        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    long getCurrentOutputSize() {
        if (byteCountOutputStream == null) {
            return 0;
        }
        return byteCountOutputStream.getCount();
    }

    /**
     * @param outputPaths the outputPaths to set
     */
    @PipelineProperty(description = "One or more destination paths for output files separated with commas. Replacement variables can be used in path strings such as ${feed}.")
    public void setOutputPaths(final String outputPaths) {
        this.outputPaths = outputPaths.split(",");
    }

    @SuppressWarnings("unused")
    @PipelineProperty(description = "When the current output file exceeds this size it will be closed and a new one created.")
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }
}
