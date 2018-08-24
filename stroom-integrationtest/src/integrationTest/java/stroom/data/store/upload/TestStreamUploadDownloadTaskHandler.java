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

package stroom.data.store.upload;

import org.junit.Assert;
import org.junit.Test;
import stroom.data.meta.api.ExpressionUtil;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataProperties;
import stroom.data.meta.api.DataStatus;
import stroom.data.store.StreamDownloadSettings;
import stroom.data.store.StreamDownloadTask;
import stroom.data.store.StreamUploadTask;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.data.store.api.StreamTarget;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipFileType;
import stroom.security.util.UserTokenUtil;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestStreamUploadDownloadTaskHandler extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private StreamStore streamStore;
    @Inject
    private DataMetaService streamMetaService;
    @Inject
    private TaskManager taskManager;

    @Test
    public void testDownload() throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".zip");
        final FindDataCriteria findStreamCriteria = new FindDataCriteria();
        findStreamCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        Assert.assertEquals(2, streamMetaService.find(findStreamCriteria).size());

        taskManager.exec(new StreamDownloadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, findStreamCriteria, file, streamDownloadSettings));

        Assert.assertEquals(2, streamMetaService.find(findStreamCriteria).size());

        final StroomZipFile stroomZipFile = new StroomZipFile(file);
        Assert.assertTrue(stroomZipFile.containsEntry("001", StroomZipFileType.Manifest));
        Assert.assertTrue(stroomZipFile.containsEntry("001", StroomZipFileType.Data));
        Assert.assertFalse(stroomZipFile.containsEntry("001", StroomZipFileType.Context));
        Assert.assertFalse(stroomZipFile.containsEntry("001", StroomZipFileType.Meta));
        stroomZipFile.close();

        taskManager.exec(new StreamUploadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "test.zip", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, null));

        Assert.assertEquals(4, streamMetaService.find(findStreamCriteria).size());
    }

    @Test
    public void testUploadFlatFile() throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final FindDataCriteria findStreamCriteria = new FindDataCriteria();
        findStreamCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));

        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".dat");
        Files.write(file, "TEST".getBytes());

        taskManager.exec(new StreamUploadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "test.dat", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, "Tom:One\nJames:Two\n"));

        Assert.assertEquals(1, streamMetaService.find(findStreamCriteria).size());
    }

    @Test
    public void testDownloadNestedComplex() throws IOException {
        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".zip");
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);

        try (final OutputStreamProvider outputStreamProvider = streamTarget.getOutputStreamProvider()) {
            try (final OutputStream outputStream = outputStreamProvider.next()) {
                outputStream.write("DATA1".getBytes(StreamUtil.DEFAULT_CHARSET));
            }

            try (final OutputStream outputStream = outputStreamProvider.next(StreamTypeNames.META)) {
                outputStream.write("META:1\nX:1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            }

            try (final OutputStream outputStream = outputStreamProvider.next(StreamTypeNames.CONTEXT)) {
                outputStream.write("CONTEXT1".getBytes(StreamUtil.DEFAULT_CHARSET));
            }

            try (final OutputStream outputStream = outputStreamProvider.next()) {
                outputStream.write("DATA2".getBytes(StreamUtil.DEFAULT_CHARSET));
            }

            try (final OutputStream outputStream = outputStreamProvider.next(StreamTypeNames.META)) {
                outputStream.write("META:2\nY:2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            }

            try (final OutputStream outputStream = outputStreamProvider.next(StreamTypeNames.CONTEXT)) {
                outputStream.write("CONTEXT2".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
        }

        streamStore.closeStreamTarget(streamTarget);

        final FindDataCriteria findStreamCriteria = new FindDataCriteria();
        findStreamCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        Assert.assertEquals(1, streamMetaService.find(findStreamCriteria).size());

        taskManager.exec(new StreamDownloadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, findStreamCriteria, file, streamDownloadSettings));

        final StroomZipFile stroomZipFile = new StroomZipFile(file);
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Manifest));
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Meta));
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Context));
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Data));
        Assert.assertTrue(stroomZipFile.containsEntry("001_2", StroomZipFileType.Meta));
        Assert.assertTrue(stroomZipFile.containsEntry("001_2", StroomZipFileType.Context));
        Assert.assertTrue(stroomZipFile.containsEntry("001_2", StroomZipFileType.Data));
        stroomZipFile.close();

        final String extraMeta = "Z:ALL\n";

        taskManager.exec(new StreamUploadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "test.zip", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, extraMeta));

        final List<Data> streamList = streamMetaService.find(findStreamCriteria);

        Assert.assertEquals(2, streamList.size());

        final Data originalStream = streamTarget.getStream();

        for (final Data stream : streamList) {
            Assert.assertEquals(DataStatus.UNLOCKED, stream.getStatus());
            final StreamSource streamSource = streamStore.openStreamSource(stream.getId());

            Assert.assertEquals("DATA1DATA2", StreamUtil.streamToString(streamSource.getInputStream(), false));
            Assert.assertEquals("CONTEXT1CONTEXT2",
                    StreamUtil.streamToString(streamSource.getChildStream(StreamTypeNames.CONTEXT).getInputStream(), false));

            if (originalStream.equals(stream)) {
                assertContains(
                        StreamUtil.streamToString(streamSource.getChildStream(StreamTypeNames.META).getInputStream(), false),
                        "META:1", "X:1", "META:2", "Y:2");
            } else {
                assertContains(
                        StreamUtil.streamToString(streamSource.getChildStream(StreamTypeNames.META).getInputStream(), false),
                        "Compression:ZIP\n", "META:1\n", "X:1\n", "Z:ALL\n", "Compression:ZIP\n", "META:2\n", "Y:2\n",
                        "Z:ALL\n");
            }

            streamStore.closeStreamSource(streamSource);
        }
    }

    private void assertContains(final String str, final String... testList) {
        for (final String test : testList) {
            Assert.assertTrue("Expecting " + str + " to contain " + test, str.contains(test));
        }
    }
}
