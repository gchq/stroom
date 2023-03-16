package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.FsPathHelper.DecodedPath;
import stroom.test.common.TestUtil;
import stroom.util.NullSafe;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mock;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

class TestFsPathHelper {

    @Mock
    private FsFeedPathDao mockFsFeedPathDao;
    @Mock
    private FsTypePathDao mockFsTypePathDao;
    @Mock
    private StreamTypeExtensions mockStreamTypeExtensions;

    @SuppressWarnings("checkstyle:LineLength")
    @TestFactory
    Stream<DynamicTest> testGetId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(long.class)
                .withTestFunction(testCase ->
                        FsPathHelper.getId(NullSafe.get(testCase.getInput(), Path::of)))
                .withSimpleEqualityAssertion()
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=414.revt.meta.bgz", 414L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=000.revt.meta.bgz", 0L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=0.revt.meta.bgz", 0L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=1.revt.meta.bgz", 1L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=001.revt.meta.bgz", 1L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=BAD_NUMBER.revt.meta.bgz", -1L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=.revt.meta.bgz", -1L)
                .addCase("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS.revt.meta.bgz", -1L)
                .addCase("/some/path/default_stream_volume/store/EVENTS/2022/12/14/001", -1L)
                .addThrowsCase(null, NullPointerException.class)
                .build();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void decodedPath1() {
        Path path = Path.of("/some/path/default_stream_volume/store/RAW_EVENTS/2022/12/14/TEST_REFERENCE_DATA-EVENTS=414.revt.meta.bgz");
        final DecodedPath decodedPath = FsPathHelper.decodedPath(path);

        Assertions.assertThat(decodedPath.getTypeName())
                .isEqualTo("RAW_EVENTS");
        Assertions.assertThat(decodedPath.getDate())
                .isEqualTo(LocalDate.of(2022, 12, 14));
        Assertions.assertThat(decodedPath.getFeedName())
                .isEqualTo("TEST_REFERENCE_DATA-EVENTS");
        Assertions.assertThat(decodedPath.getMetaId())
                .isEqualTo(414L);
        Assertions.assertThat(decodedPath.isDirectory())
                .isFalse();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void decodedPath2() {
        Path path = Path.of("/some/path/default_stream_volume/store/RAW_EVENTS/2023/03/15/005/TEST_FEED=999999.revt.meta.bgz.yml");
        final DecodedPath decodedPath = FsPathHelper.decodedPath(path);

        Assertions.assertThat(decodedPath.getTypeName())
                .isEqualTo("RAW_EVENTS");
        Assertions.assertThat(decodedPath.getDate())
                .isEqualTo(LocalDate.of(2023, 3, 15));
        Assertions.assertThat(decodedPath.getFeedName())
                .isEqualTo("TEST_FEED");
        Assertions.assertThat(decodedPath.getMetaId())
                .isEqualTo(999_999L);
        Assertions.assertThat(decodedPath.isDirectory())
                .isFalse();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void decodedPath3() {
        Path path = Path.of("/some/path/default_stream_volume/store/EVENTS/2023/01/16/002");
        final DecodedPath decodedPath = FsPathHelper.decodedPath(path);

        Assertions.assertThat(decodedPath.getTypeName())
                .isEqualTo("EVENTS");
        Assertions.assertThat(decodedPath.getDate())
                .isEqualTo(LocalDate.of(2023, 1, 16));
        Assertions.assertThat(decodedPath.getFeedName())
                .isNull();
        Assertions.assertThat(decodedPath.getMetaId())
                .isNull();
        Assertions.assertThat(decodedPath.isDirectory())
                .isTrue();
    }
}
