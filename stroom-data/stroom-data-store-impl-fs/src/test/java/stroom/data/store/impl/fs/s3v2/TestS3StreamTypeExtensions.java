/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v2;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.data.store.impl.fs.standard.InternalStreamTypeNames;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestS3StreamTypeExtensions {

    @Mock
    private FsVolumeConfig mockFsVolumeConfig;
    @Mock
    private DataVolume mockDataVolume;

    @TestFactory
    Stream<DynamicTest> getExtension() {
        final S3StreamTypeExtensions s3StreamTypeExtensions = new S3StreamTypeExtensions(FsVolumeConfig::new);

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        s3StreamTypeExtensions.getExtension(testCase.getInput()._1(), testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(StreamTypeNames.EVENTS, null), ".evt.zst")
                .addCase(
                        Tuple.of(StreamTypeNames.EVENTS, InternalStreamTypeNames.MANIFEST),
                        ".evt.mf.zst")
                .addCase(
                        Tuple.of(StreamTypeNames.EVENTS, StreamTypeNames.META),
                        ".evt.meta.zst")
                .addCase(
                        Tuple.of(StreamTypeNames.RAW_EVENTS, StreamTypeNames.CONTEXT),
                        ".revt.ctx.zst")
                .addCase(
                        Tuple.of(StreamTypeNames.RAW_EVENTS, StreamTypeNames.META),
                        ".revt.meta.zst")
                .addCase(
                        Tuple.of(StreamTypeNames.DETECTIONS, null),
                        ".dtxn.zst")
                .addThrowsCase(Tuple.of(null, StreamTypeNames.META), RuntimeException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> getChildType() {
        final S3StreamTypeExtensions s3StreamTypeExtensions = new S3StreamTypeExtensions(FsVolumeConfig::new);
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(s3StreamTypeExtensions::getChildType)
                .withSimpleEqualityAssertion()
                .addCase(".evt.mf.zst", InternalStreamTypeNames.MANIFEST)
                .addCase(".evt.meta.zst", StreamTypeNames.META)
                .addCase(".evt.ctx.zst", StreamTypeNames.CONTEXT)
                .addCase(".evt.zst", null)
                .addThrowsCase(".evt.foo.zst", RuntimeException.class)
                .addThrowsCase(".evt.foo", RuntimeException.class)
                .addThrowsCase(".evt", RuntimeException.class)
                .addThrowsCase(".foo", RuntimeException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetKey() {
        final S3StreamTypeExtensions s3StreamTypeExtensions = new S3StreamTypeExtensions(FsVolumeConfig::new);
        final int volId = 123;
        Mockito.when(mockDataVolume.getVolumeId())
                .thenReturn(volId);
        return TestUtil.buildDynamicTestStream()
                .withInputType(FileKey.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String key = s3StreamTypeExtensions.getkey(testCase.getInput());
                    // Makes sure we can reverse the conversion
                    final FileKey fileKey2 = s3StreamTypeExtensions.getFileKey(mockDataVolume, key);
                    assertThat(fileKey2)
                            .isEqualTo(testCase.getInput());
                    return key;
                })
                .withSimpleEqualityAssertion()
                .addCase(new FileKey(volId, 1, StreamTypeNames.RAW_EVENTS, null),
                        "01/0000000000000000001/1.revt.zst")
                .addCase(new FileKey(
                                volId,
                                123_456L,
                                StreamTypeNames.RAW_EVENTS,
                                InternalStreamTypeNames.MANIFEST),
                        "56/0000000000000123456/123456.revt.mf.zst")
                .addCase(new FileKey(
                                volId,
                                123_456_789L,
                                StreamTypeNames.EVENTS,
                                StreamTypeNames.CONTEXT),
                        "89/0000000000123456789/123456789.evt.ctx.zst")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetDictKey() {
        final S3StreamTypeExtensions s3StreamTypeExtensions = new S3StreamTypeExtensions(FsVolumeConfig::new);
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String key = s3StreamTypeExtensions.getDictkey(testCase.getInput());
                    // Makes sure we can reverse the conversion
                    final String uuid2 = s3StreamTypeExtensions.getDictUuid(key);
                    assertThat(uuid2)
                            .isEqualTo(testCase.getInput());
                    return key;
                })
                .withSimpleEqualityAssertion()
                .addCase("cab862f3-75d3-455d-ad44-58009c19788d",
                        "dict/cab862f3-75d3-455d-ad44-58009c19788d.zdict")
                .build();
    }
}
