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

package stroom.data.store.upload;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.DataDownloadSettings;
import stroom.data.store.impl.DataDownloadTaskHandler;
import stroom.data.store.impl.DataUploadTaskHandler;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.Status;
import stroom.task.api.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.StreamUtil;
import stroom.util.string.StringIdUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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
    @Inject
    private DataUploadTaskHandler dataUploadTaskHandler;
    @Inject
    private DataDownloadTaskHandler dataDownloadTaskHandler;

    @Test
    void testDownload() throws IOException {
        final int entryCount = 2;

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        for (int i = 1; i <= entryCount; i++) {
            commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        }

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(MetaExpressionUtil.createFeedExpression(feedName));

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(entryCount);

        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".zip");
        final DataDownloadSettings streamDownloadSettings = new DataDownloadSettings();

        String format = file.getFileName().toString();
        format = format.substring(0, format.indexOf("."));
        dataDownloadTaskHandler.downloadData(findMetaCriteria, getCurrentTestDir(), format, streamDownloadSettings);

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(entryCount);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
            for (int i = 1; i <= entryCount; i++) {
                final String baseName = StringIdUtil.idToString(i);
                assertThat(stroomZipFile.containsEntry(baseName, StroomZipFileType.MANIFEST)).isTrue();
                assertThat(stroomZipFile.containsEntry(baseName, StroomZipFileType.DATA)).isTrue();
                assertThat(stroomZipFile.containsEntry(baseName, StroomZipFileType.CONTEXT)).isFalse();
                assertThat(stroomZipFile.containsEntry(baseName, StroomZipFileType.META)).isFalse();
            }
        }

        dataUploadTaskHandler.uploadData("test.zip", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, null);

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(entryCount + 1);
    }

    @Test
    void testUploadFlatFile() throws IOException {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(MetaExpressionUtil.createFeedExpression(feedName));

        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".dat");
        Files.write(file, "TEST".getBytes());

        dataUploadTaskHandler.uploadData("test.dat", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, "Foo:One\nBar:Two\n");

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1);
    }

    @Test
    void testDownloadNestedComplex() throws IOException {
        final Path file = Files.createTempFile(getCurrentTestDir(), "TestStreamDownloadTaskHandler", ".zip");
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final Meta originalMeta;

        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
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
        findMetaCriteria.setExpression(MetaExpressionUtil.createFeedExpression(feedName));
        final DataDownloadSettings streamDownloadSettings = new DataDownloadSettings();

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1);

        String format = file.getFileName().toString();
        format = format.substring(0, format.indexOf("."));
        dataDownloadTaskHandler.downloadData(findMetaCriteria, getCurrentTestDir(), format, streamDownloadSettings);

        try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
            assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.MANIFEST)).isTrue();
            assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.META)).isTrue();
            assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.CONTEXT)).isTrue();
            assertThat(stroomZipFile.containsEntry("001_1", StroomZipFileType.DATA)).isTrue();
            assertThat(stroomZipFile.containsEntry("001_2", StroomZipFileType.META)).isTrue();
            assertThat(stroomZipFile.containsEntry("001_2", StroomZipFileType.CONTEXT)).isTrue();
            assertThat(stroomZipFile.containsEntry("001_2", StroomZipFileType.DATA)).isTrue();
        }

        final String extraMeta = "Z:ALL\n";

        dataUploadTaskHandler.uploadData("test.zip", file, feedName,
                StreamTypeNames.RAW_EVENTS, null, extraMeta);

        final List<Meta> streamList = metaService.find(findMetaCriteria).getValues();

        assertThat(streamList.size()).isEqualTo(2);

        for (final Meta meta : streamList) {
            assertThat(meta.getStatus()).isEqualTo(Status.UNLOCKED);
            try (final Source streamSource = streamStore.openSource(meta.getId())) {
                for (int i = 1; i <= streamSource.count(); i++) {
                    try (final InputStreamProvider inputStreamProvider = streamSource.get(i - 1)) {
                        assertThat(StreamUtil.streamToString(inputStreamProvider.get(), false))
                                .isEqualTo("DATA" + i);
                        assertThat(StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.CONTEXT),
                                false))
                                .isEqualTo("CONTEXT" + i);

                        if (originalMeta.equals(meta)) {
                            assertContains(StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.META),
                                    false),
                                    "META:" + i, "X:" + i);
                        } else {
                            assertContains(StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.META),
                                    false),
                                    "UploadedBy:admin", "META:" + i + "\n", "X:" + i + "\n", "Z:ALL\n");
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
