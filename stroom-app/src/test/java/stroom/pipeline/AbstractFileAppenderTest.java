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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.nio.file.Path;

abstract class AbstractFileAppenderTest extends AbstractAppenderTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileAppenderTest.class);

    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    void test(final DocRef pipelineRef,
              final String dir,
              final String name,
              final String type,
              final String outputReference,
              final String encoding) {
        final Path tempDir = getCurrentTestDir();

        // Make sure the config dir is set.
        FileUtil.setTempDir(tempDir);

        LOGGER.debug("Setting tempDir to {}", FileUtil.getCanonicalPath(tempDir));

        // Delete any output file.
        final String outputFileName = name + "_" + type;
        final Path outputFile = tempDir.resolve(outputFileName + ".tmp");
        final Path outputLockFile = tempDir.resolve(outputFileName + ".lock");
        FileUtil.deleteFile(outputFile);
        FileUtil.deleteFile(outputLockFile);

        pipelineScopeRunnable.scopeRunnable(() -> {
            super.process(pipelineRef, dir, name, encoding);
            super.validateProcess();
        });

        final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(outputReference);
        ComparisonHelper.compareFiles(refFile, outputFile, false, false);
    }
}
