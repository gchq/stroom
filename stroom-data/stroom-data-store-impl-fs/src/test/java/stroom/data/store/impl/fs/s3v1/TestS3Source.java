/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v1;

import stroom.aws.s3.impl.S3FileExtensions;
import stroom.aws.s3.shared.S3Location;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.impl.fs.RASegmentOutputStream;
import stroom.meta.shared.Meta;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TestS3Source {

    /// Confirms that absent optional child streams are treated as absent rather than as errors.
    @Test
    void testMissingChildStreams(@TempDir final Path tempDir) throws IOException {
        final S3Source source = createSource(tempDir);

        try (source; final InputStreamProvider inputStreamProvider = source.get(0)) {
            assertThat(inputStreamProvider.getChildTypes()).isEmpty();
            assertThat(inputStreamProvider.get(StreamTypeNames.META)).isNull();
            assertThat(inputStreamProvider.get(StreamTypeNames.CONTEXT)).isNull();
        }
    }

    /// Confirms that existing optional child streams can still be opened.
    @Test
    void testExistingChildStreams(@TempDir final Path tempDir) throws IOException {
        writeChildStream(tempDir, S3FileExtensions.META_EXTENSION, "meta");
        writeChildStream(tempDir, S3FileExtensions.CONTEXT_EXTENSION, "context");

        final S3Source source = createSource(tempDir);
        try (source; final InputStreamProvider inputStreamProvider = source.get(0)) {
            assertThat(inputStreamProvider.getChildTypes())
                    .containsExactlyInAnyOrder(StreamTypeNames.META, StreamTypeNames.CONTEXT);

            try (final SegmentInputStream metaStream = inputStreamProvider.get(StreamTypeNames.META);
                    final SegmentInputStream contextStream = inputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                assertThat(StreamUtil.streamToString(metaStream)).isEqualTo("meta");
                assertThat(StreamUtil.streamToString(contextStream)).isEqualTo("context");
            }
        }
    }

    private S3Source createSource(final Path tempDir) {
        return new S3Source(
                mock(S3StreamStore.class),
                tempDir,
                new S3Location("region", "bucket", "key"),
                mock(Meta.class),
                FilePadStyle.MULTIPLE_OF_THREE_DIGITS);
    }

    private void writeChildStream(final Path tempDir,
                                  final String extension,
                                  final String content) throws IOException {
        final Path dataFile = tempDir.resolve("001" + extension);
        final Path indexFile = tempDir.resolve("001" + extension + S3FileExtensions.INDEX_EXTENSION);

        try (final OutputStream outputStream = Files.newOutputStream(dataFile);
                final SegmentOutputStream segmentOutputStream = new RASegmentOutputStream(
                        outputStream,
                        () -> Files.newOutputStream(indexFile))) {
            segmentOutputStream.write(content.getBytes(StreamUtil.DEFAULT_CHARSET));
            segmentOutputStream.addSegment();
        }
    }
}
