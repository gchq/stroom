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

package stroom.pipeline.server.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.CommonTestControl;
import stroom.test.StroomProcessTestFileUtil;
import org.junit.Assert;
import org.junit.Test;

import stroom.AbstractProcessIntegrationTest;
import stroom.CommonTranslationTest;
import stroom.streamstore.server.MockStreamStore;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.server.StreamProcessorTaskExecutor;
import stroom.test.ComparisonHelper;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

public class TestTranslationTask extends AbstractProcessIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTranslationTask.class);

    private static final int N3 = 3;
    private static final int N4 = 4;

    private static final String DIR = "TestTranslationTask/";

    @Resource
    private MockStreamStore streamStore;
    @Resource
    private CommonTranslationTest commonPipelineTest;
    @Resource
    private CommonTestControl commonTestControl;

    /**
     * Tests Task with a valid resource and feed.
     *
     * @throws Exception
     *             Could be thrown.
     */
    @Test
    public void testBothValid() throws Exception {
        final Path csv = Paths.get("/Users/stroomdev66/tmp/performance.csv");
        final BufferedWriter writer = Files.newBufferedWriter(csv, Charset.forName("UTF-8"));

        for (int i = 1; i <= 100; i++) {
            commonTestControl.teardown();
            commonTestControl.setup();
            streamStore.clear();

            LOGGER.info("Run " + i);

            long setupTime = System.currentTimeMillis();
            commonPipelineTest.setup();
            setupTime = System.currentTimeMillis() - setupTime;
            LOGGER.info("Setup ran in " + ModelStringUtil.formatDurationString(setupTime) + " (" + setupTime + ")");

            long processTime = System.currentTimeMillis();
            final List<StreamProcessorTaskExecutor> results = commonPipelineTest.processAll();
            processTime = System.currentTimeMillis() - processTime;
            LOGGER.info("Process ran in " + ModelStringUtil.formatDurationString(processTime) + " (" + processTime + ")");

//            writer.write(String.valueOf(i));
//            writer.write(",");
            writer.write(String.valueOf(processTime));
            writer.write("\n");
            writer.flush();
        }

        writer.close();

//        Assert.assertEquals(N4, results.size());
//        for (final StreamProcessorTaskExecutor result : results) {
//            final PipelineStreamProcessor processor = (PipelineStreamProcessor) result;
//            Assert.assertTrue(result.toString(), processor.getWritten() > 0);
//            Assert.assertTrue(result.toString(), processor.getRead() <= processor.getWritten());
//            Assert.assertEquals(result.toString(), 0, processor.getMarkerCount(Severity.SEVERITIES));
//        }
//
//        final File inputDir = new File(StroomProcessTestFileUtil.getTestResourcesDir(), DIR);
//        final File outputDir = new File(StroomProcessTestFileUtil.getTestOutputDir(), DIR);
//
//        for (final Stream stream : streamStore.getFileData().keySet()) {
//            if (stream.getStreamType().equals(StreamType.EVENTS)) {
//                final byte[] data = streamStore.getFileData().get(stream).get(stream.getStreamType().getId());
//
//                // Write the actual XML out.
//                final OutputStream os = StroomProcessTestFileUtil.getOutputStream(outputDir, "TestTranslationTask.out");
//                os.write(data);
//                os.flush();
//                os.close();
//
//                ComparisonHelper.compareFiles(new File(inputDir, "TestTranslationTask.out"),
//                        new File(outputDir, "TestTranslationTask.out"));
//            }
//        }
//
//        // Make sure 26 records were written.
//        Assert.assertEquals(26, ((PipelineStreamProcessor) results.get(N3)).getWritten());
    }

    /**
     * Tests Task with an invalid resource and valid feed.
     *
     * @throws Exception
     *             Could be thrown.
     */
    @Test
    public void testInvalidResource() throws Exception {
        commonPipelineTest.setup(CommonTranslationTest.FEED_NAME, CommonTranslationTest.INVALID_RESOURCE_NAME);

        final List<StreamProcessorTaskExecutor> results = commonPipelineTest.processAll();
        Assert.assertEquals(N4, results.size());

        // Make sure no records were written.
        Assert.assertEquals(0, ((PipelineStreamProcessor) results.get(N3)).getWritten());
    }
}
