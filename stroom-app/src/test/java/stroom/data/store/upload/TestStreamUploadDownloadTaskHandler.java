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


import org.junit.jupiter.api.Test;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.Status;
import stroom.meta.shared.ExpressionUtil;
import stroom.meta.shared.FindMetaCriteria;
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
import stroom.task.api.TaskManager;
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

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamUploadDownloadTaskHandler extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private StreamStore streamStore;
    @Inject
    private MetaService metaService;
    @Inject
    private TaskManager taskManager;

    @Test
    void testDownload() throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".zip");
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(2);

        taskManager.exec(new StreamDownloadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, findMetaCriteria, file, streamDownloadSettings));

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(2);

        final StroomZipFile stroomZipFile = new StroomZipFile(file);
        assertThat(stroomZipFile.containsEntry("001", StroomZipFileType.Manifest)).isTrue();
        assertThat(stroomZipFile.containsEntry("001", StroomZipFileType.Data)).isTrue();
        assertThat(stroomZipFile.containsEntry("001", StroomZipFileType.Context)).isFalse();
        assertThat(stroomZipFile.containsEntry("001", StroomZipFileType.Meta)).isFalse();
        stroomZipFile.close();

        taskManager.exec(new StreamUploadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "test.zip", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, null));

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(4);
    }

    @Test
    void testUploadFlatFile() throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));

        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".dat");
        Files.write(file, "TEST".getBytes());

        taskManager.exec(new StreamUploadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "test.dat", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, "Tom:One\nJames:Two\n"));

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1);
    }

    @Test
    void testDownloadNestedComplex() throws IOException {
        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".zip");
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final StreamTarget streamTarget = streamStore.openStreamTarget(metaProperties);

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

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1);

        taskManager.exec(new StreamDownloadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, findMetaCriteria, file, streamDownloadSettings));

        final StroomZipFile stroomZipFile = new StroomZipFile(file);
        assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.Manifest)).isTrue();
        assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.Meta)).isTrue();
        assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.Context)).isTrue();
        assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.Data)).isTrue();
        assertThat(stroomZipFile.containsEntry("001_2", StroomZipFileType.Meta)).isTrue();
        assertThat(stroomZipFile.containsEntry("001_2", StroomZipFileType.Context)).isTrue();
        assertThat(stroomZipFile.containsEntry("001_2", StroomZipFileType.Data)).isTrue();
        stroomZipFile.close();

        final String extraMeta = "Z:ALL\n";

        taskManager.exec(new StreamUploadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "test.zip", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, extraMeta));

        final List<Meta> streamList = metaService.find(findMetaCriteria);

        assertThat(streamList.size()).isEqualTo(2);

        final Meta originalMeta = streamTarget.getMeta();

        for (final Meta meta : streamList) {
            assertThat(meta.getStatus()).isEqualTo(Status.UNLOCKED);
            final StreamSource streamSource = streamStore.openStreamSource(meta.getId());

            assertThat(StreamUtil.streamToString(streamSource.getInputStream(), false)).isEqualTo("DATA1DATA2");
            assertThat(StreamUtil.streamToString(streamSource.getChildStream(StreamTypeNames.CONTEXT).getInputStream(), false)).isEqualTo("CONTEXT1CONTEXT2");

            if (originalMeta.equals(meta)) {
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
            assertThat(str.contains(test)).as("Expecting " + str + " to contain " + test).isTrue();
        }
    }
}
