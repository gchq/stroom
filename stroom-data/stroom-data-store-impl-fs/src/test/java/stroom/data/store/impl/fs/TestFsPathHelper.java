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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.FsPathHelper.DecodedPath;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.SimpleMetaImpl;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestFsPathHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFsPathHelper.class);

    @Mock
    private FsFeedPathDao mockFsFeedPathDao;
    @Mock
    private FsTypePathDao mockFsTypePathDao;
    @Mock
    private StreamTypeExtensions mockStreamTypeExtensions;
    @InjectMocks
    private FsPathHelper fsPathHelper;

    @SuppressWarnings("checkstyle:LineLength")
    @TestFactory
    Stream<DynamicTest> testGetId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(long.class)
                .withTestFunction(testCase ->
                        FsPathHelper.getId(NullSafe.get(testCase.getInput(), Path::of)))
                .withSimpleEqualityAssertion()
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=414.revt.meta.bgz",
                        414L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=000.revt.meta.bgz",
                        0L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=0.revt.meta.bgz",
                        0L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=1.revt.meta.bgz",
                        1L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=001.revt.meta.bgz",
                        1L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=BAD_NUMBER.revt.meta.bgz",
                        -1L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=.revt.meta.bgz",
                        -1L)
                .addCase(
                        "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS.revt.meta.bgz",
                        -1L)
                .addCase("/some/path/default_stream_volume/store/EVENTS/2022/12/14/001", -1L)
                .addThrowsCase(null, NullPointerException.class)
                .build();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void decodedPath1() {
        final Path path = Path.of(
                "/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=414.revt.meta.bgz");
        final DecodedPath decodedPath = FsPathHelper.decodedPath(path);

        assertThat(decodedPath.getTypeName())
                .isEqualTo("RAW_EVENTS");
        assertThat(decodedPath.getDate())
                .isEqualTo(LocalDate.of(2022, 12, 14));
        assertThat(decodedPath.getFeedName())
                .isEqualTo("TEST_REFERENCE_DATA-EVENTS");
        assertThat(decodedPath.getMetaId())
                .isEqualTo(414L);
        assertThat(decodedPath.isDirectory())
                .isFalse();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void decodedPath2() {
        final Path path = Path.of(
                "/some/path/default_stream_volume/store/RAW_EVENTS/2023/03/15/005/TEST_FEED=999999.revt.meta.bgz.yml");
        final DecodedPath decodedPath = FsPathHelper.decodedPath(path);

        assertThat(decodedPath.getTypeName())
                .isEqualTo("RAW_EVENTS");
        assertThat(decodedPath.getDate())
                .isEqualTo(LocalDate.of(2023, 3, 15));
        assertThat(decodedPath.getFeedName())
                .isEqualTo("TEST_FEED");
        assertThat(decodedPath.getMetaId())
                .isEqualTo(999_999L);
        assertThat(decodedPath.isDirectory())
                .isFalse();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void decodedPath3() {
        final Path path = Path.of("/some/path/default_stream_volume/store/EVENTS/2023/01/16/002");
        final DecodedPath decodedPath = FsPathHelper.decodedPath(path);

        assertThat(decodedPath.getTypeName())
                .isEqualTo("EVENTS");
        assertThat(decodedPath.getDate())
                .isEqualTo(LocalDate.of(2023, 1, 16));
        assertThat(decodedPath.getFeedName())
                .isNull();
        assertThat(decodedPath.getMetaId())
                .isNull();
        assertThat(decodedPath.isDirectory())
                .isTrue();
    }

    @Test
    void testGetRootPath() {


        final Path volPath = Path.of("/some/path");
        final long createMs = LocalDate.of(2023, 3, 17)
                .atTime(LocalTime.MIDNIGHT)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        final String streamType = StreamTypeNames.RAW_EVENTS;
        final String streamTypeAsPath = StreamTypeNames.RAW_EVENTS.toUpperCase().replace(' ', '_');
        final String feed = "FEED_ME";
        final long metaId = 123_456_789L;
        final SimpleMeta simpleMeta = new SimpleMetaImpl(
                metaId,
                streamType,
                feed,
                createMs,
                createMs);
        final FsVolumeConfig fsVolumeConfig = new FsVolumeConfig();
        final String metaTypeExt = fsVolumeConfig.getMetaTypeExtension(streamType).orElseThrow();

        Mockito.when(mockFsFeedPathDao.getOrCreatePath(Mockito.anyString()))
                .thenReturn(feed);
        Mockito.when(mockFsTypePathDao.getOrCreatePath(Mockito.anyString()))
                .thenReturn(streamTypeAsPath);
        Mockito.when(mockStreamTypeExtensions.getExtension(Mockito.anyString()))
                .thenReturn(metaTypeExt);

        final Path rootPath = fsPathHelper.getRootPath(volPath, simpleMeta);

        LOGGER.debug("rootPath: {}", rootPath);
        final List<String> pathParts = new ArrayList<>(rootPath.getNameCount());
        for (final Path path : rootPath) {
            pathParts.add(path.getFileName().toString());
        }

        assertThat(pathParts)
                .containsExactly(
                        "some",
                        "path",
                        "store",
                        streamTypeAsPath,
                        "2023",
                        "03",
                        "17",
                        "123", // 1st 3 digits of metaId
                        "456", // 2st 3 digits of metaId
                        feed + FsPathHelper.FILE_SEPARATOR_CHAR + metaId + "." + metaTypeExt + ".bgz");
    }
}
