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
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.DataDownloadTask;
import stroom.data.store.impl.StreamDownloadSettings;
import stroom.data.store.impl.StreamUploadTask;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.shared.ExpressionUtil;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
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
    private Store streamStore;
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

        taskManager.exec(new DataDownloadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, findMetaCriteria, file, streamDownloadSettings));

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
        Meta originalMeta;

        try (final Target streamTarget = streamStore.openStreamTarget(metaProperties)) {
            originalMeta = streamTarget.getMeta();

            for (int i = 1; i <= 2; i++) {
                try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
                    try (final OutputStream outputStream = outputStreamProvider.get()) {
                        outputStream.write(("DATA" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                    }

                    try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.META)) {
                        outputStream.write(("META:" + i + "\nX:" + i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
                    }

                    try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                        outputStream.write(("CONTEXT" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
                    }
                }
            }
        }

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(ExpressionUtil.createFeedExpression(feedName));
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1);

        taskManager.exec(new DataDownloadTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, findMetaCriteria, file, streamDownloadSettings));

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

        for (final Meta meta : streamList) {
            assertThat(meta.getStatus()).isEqualTo(Status.UNLOCKED);
            try (final Source streamSource = streamStore.openStreamSource(meta.getId())) {
                for (int i = 1; i <= streamSource.count(); i++) {
                    try (final InputStreamProvider inputStreamProvider = streamSource.get(i - 1)) {
                        assertThat(StreamUtil.streamToString(inputStreamProvider.get(), false)).isEqualTo("DATA" + i);
                        assertThat(StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.CONTEXT), false)).isEqualTo("CONTEXT" + i);

                        if (originalMeta.equals(meta)) {
                            assertContains(StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.META), false), "META:" + i, "X:" + i);
                        } else {
                            assertContains(StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.META), false), "Compression:ZIP\n", "META:" + i + "\n", "X:" + i + "\n", "Z:ALL\n");
                        }
                    }
                }
            }
        }
    }

    private void assertContains(final String str, final String... testList) {
        for (final String test : testList) {
            assertThat(str.contains(test)).as("Expecting " + str + " to contain " + test).isTrue();
        }
    }
}
