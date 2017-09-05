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
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingFileDestination;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;

/**
 * Joins text instances into a single text instance.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "RollingFileAppender", category = Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class RollingFileAppender extends AbstractRollingAppender {

    @Resource
    private PathCreator pathCreator;

    private String[] outputPaths;
    private String fileNamePattern;
    private String rolledFileNamePattern;

    private String dir;
    private String fileName;
    private String rolledFileName;
    private String key;

    @Override
    public RollingDestination createDestination() throws IOException {
        String dir = this.dir;
        String fileName = this.fileName;

        dir = PathCreator.replaceTimeVars(dir);
        dir = PathCreator.replaceUUIDVars(dir);

        fileName = PathCreator.replaceTimeVars(fileName);
        fileName = PathCreator.replaceUUIDVars(fileName);

        // Create a new destination.
        final File file = new File(dir + File.separator + fileName);

        // Try and create the path.
        final File parentDir = file.getParentFile();
        if (!parentDir.isDirectory()) {
            if (!parentDir.mkdirs()) {
                throw new ProcessException("Unable to create output dirs: " + parentDir.getAbsolutePath());
            }
        }

        return new RollingFileDestination(key,
                getFrequency(),
                getMaxSize(),
                System.currentTimeMillis(),
                fileName,
                rolledFileName,
                parentDir,
                file);
    }

    @Override
    Object getKey() throws IOException {
        try {
            // Create the current file name if one isn't set.
            if (key == null) {
                dir = getRandomOutputPath();
                dir = pathCreator.replaceContextVars(dir);
                dir = PathCreator.replaceSystemProperties(dir);

                fileName = fileNamePattern;
                fileName = pathCreator.replaceContextVars(fileName);
                fileName = PathCreator.replaceSystemProperties(fileName);

                rolledFileName = rolledFileNamePattern;
                rolledFileName = pathCreator.replaceContextVars(rolledFileName);
                rolledFileName = PathCreator.replaceSystemProperties(rolledFileName);

                key = dir + File.separator + fileName;
            }

            return key;
        } catch (final IOException e) {
            throw e;
        } catch (final Throwable t) {
            throw new IOException(t.getMessage(), t);
        }
    }

    private String getRandomOutputPath() throws IOException {
        if (outputPaths == null || outputPaths.length == 0) {
            throw new IOException("No output paths have been set");
        }

        // Get a path to use.
        String path = null;
        if (outputPaths.length == 1) {
            path = outputPaths[0];
        } else {
            // Choose one of the output paths at random.
            path = outputPaths[(int) Math.round(Math.random() * (outputPaths.length - 1))];
        }

        return path;
    }

    @Override
    void validateSpecificSettings() {
        if (outputPaths == null || outputPaths.length == 0) {
            throw new ProcessException("No output paths have been specified");
        }

        if (fileNamePattern == null || fileNamePattern.length() == 0) {
            throw new ProcessException("No file name has been specified");
        }

        if (rolledFileNamePattern == null || rolledFileNamePattern.length() == 0) {
            throw new ProcessException("No rolled file name has been specified");
        }

        if (fileNamePattern.equals(rolledFileNamePattern)) {
            throw new ProcessException("File name and rolled file name cannot be the same");
        }
    }

    @PipelineProperty(description = "One or more destination paths for output files separated with commas. Replacement variables can be used in path strings such as ${feed}.")
    public void setOutputPaths(final String outputPaths) {
        this.outputPaths = outputPaths.split(",");
    }

    @PipelineProperty(description = "Choose the name of the file to write.")
    public void setFileName(final String fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    @PipelineProperty(description = "Choose the name that files will be renamed to when they are rolled.")
    public void setRolledFileName(final String rolledFileNamePattern) {
        this.rolledFileNamePattern = rolledFileNamePattern;
    }
}
