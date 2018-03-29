/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline;

import stroom.pipeline.shared.PipelineEntity;
import stroom.test.ComparisonHelper;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;

import java.nio.file.Path;

public abstract class AbstractFileAppenderTest extends AbstractAppenderTest {
    void test(final PipelineEntity pipelineEntity,
              final String dir,
              final String name,
              final String type,
              final String outputReference,
              final String encoding) {
        final Path tempDir = getCurrentTestDir();

        // Make sure the config dir is set.
        System.setProperty("stroom.temp", FileUtil.getCanonicalPath(tempDir));

        // Delete any output file.
        final String outputFileName = name + "_" + type;
        final Path outputFile = tempDir.resolve(outputFileName + ".tmp");
        final Path outputLockFile = tempDir.resolve(outputFileName + ".lock");
        FileUtil.deleteFile(outputFile);
        FileUtil.deleteFile(outputLockFile);

        super.test(pipelineEntity, dir, name, type, outputReference, encoding);

        final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(outputReference);
        ComparisonHelper.compareFiles(refFile, outputFile, false, false);
    }
}
