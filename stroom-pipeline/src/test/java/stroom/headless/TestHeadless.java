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

package stroom.headless;

import stroom.test.StroomProcessTestFileUtil;
import stroom.test.ComparisonHelper;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.zip.ZipUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Ignore;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;

public class TestHeadless {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestHeadless.class);

    // TODO : Add new data to test headless.

    @Ignore
    public void test() throws Exception {
        String newTempDir = null;

        try {
            // Let tests update the database
//            StroomProperties.setOverrideProperty("stroom.jpaHbm2DdlAuto", "update", "test");
//            StroomProperties.setOverrideProperty("stroom.connectionTesterClassName",
//                    "stroom.entity.server.util.StroomConnectionTesterOkOnException", "test");

            newTempDir = FileUtil.getTempDir().getCanonicalPath() + File.separator + "headless";
            StroomProperties.setOverrideProperty("stroom.temp", newTempDir, "test");

            // Make sure the new temp directory is empty.
            final File tmpDir = new File(newTempDir);
            if (tmpDir.isDirectory()) {
                FileUtils.deleteDirectory(tmpDir);
            }

            final String dir = StroomProcessTestFileUtil.getTestResourcesDir().getCanonicalPath() + File.separator
                    + "TestHeadless";

            final String inputDirPath = dir + File.separator + "input";
            final String outputDirPath = dir + File.separator + "output";
            new File(inputDirPath).mkdir();
            new File(outputDirPath).mkdir();

            final String configFilePath = dir + File.separator + "config.zip";
            final String configUnzippedDirPath = dir + File.separator + "configUnzipped";
            final String inputFilePath = inputDirPath + File.separator + "001.zip";
            final String inputUnzippedDirPath = dir + File.separator + "inputUnzipped";
            final String outputFilePath = outputDirPath + File.separator + "output";
            final String expectedOutputFilePath = dir + File.separator + "expectedOutput";

            // Clear out any files from previous runs
            Files.deleteIfExists(new File(inputFilePath).toPath());
            Files.deleteIfExists(new File(configFilePath).toPath());

            // build the input zip file from the source files
            // the zip files created are transient and are ignored by git. They
            // are left in place to make it
            // easier to see the actual file when debugging the test
            final File inputZipFile = new File(inputFilePath);
            ZipUtil.zip(inputZipFile, new File(inputUnzippedDirPath));

            // build the config zip file from the source files
            // the zip files created are transient and are ignored by git. They
            // are left in place to make it
            // easier to see the actual file when debugging the test
            final File configZipFile = new File(configFilePath);
            ZipUtil.zip(configZipFile, new File(configUnzippedDirPath));

            final Headless headless = new Headless();

            headless.setConfig(configFilePath);
            headless.setInput(inputDirPath);
            headless.setOutput(outputFilePath);
            headless.setTmp(newTempDir);
            headless.run();

            final List<String> expectedLines = Files.readAllLines(new File(expectedOutputFilePath).toPath(),
                    Charset.defaultCharset());
            final List<String> outputLines = Files.readAllLines(new File(outputFilePath).toPath(),
                    Charset.defaultCharset());

            // same number of lines output as expected
            Assert.assertEquals(expectedLines.size(), outputLines.size());

            // make sure all lines are present in both
            Assert.assertEquals(new HashSet<String>(expectedLines), new HashSet<String>(outputLines));

            // content should exactly match expected file
            ComparisonHelper.compareFiles(new File(expectedOutputFilePath), new File(outputFilePath));

        } finally {
            StroomProperties.removeOverrides();
        }
    }

}
