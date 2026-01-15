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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class TestMultiForwardDestination {

    @Mock
    private CleanupDirQueue mockCleanupDirQueue;
    @Captor
    private ArgumentCaptor<Path> pathCaptor1;
    @Captor
    private ArgumentCaptor<Path> pathCaptor2;
    @Captor
    private ArgumentCaptor<Path> pathCaptor3;

    private MockForwardFileDestination mockForwardDestination1;
    private MockForwardFileDestination mockForwardDestination2;
    private MockForwardFileDestination mockForwardDestination3;
    private Path baseDir;
    private Path copiesDir;
    private Path deletesDir;
    private Path sourcesDir;
    private Path dest1Dir;
    private Path dest2Dir;
    private Path dest3Dir;


    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        baseDir = tempDir;
        copiesDir = baseDir.resolve("copies");
        FileUtil.ensureDirExists(copiesDir);
        deletesDir = baseDir.resolve("deletes");
        FileUtil.ensureDirExists(deletesDir);
        sourcesDir = baseDir.resolve("sources");
        FileUtil.ensureDirExists(sourcesDir);
        dest1Dir = baseDir.resolve("dest1");
        FileUtil.ensureDirExists(dest1Dir);
        dest2Dir = baseDir.resolve("dest2");
        FileUtil.ensureDirExists(dest2Dir);
        dest3Dir = baseDir.resolve("dest3");
        FileUtil.ensureDirExists(dest3Dir);
        mockForwardDestination1 = new MockForwardFileDestination(dest1Dir);
        mockForwardDestination2 = new MockForwardFileDestination(dest2Dir);
        mockForwardDestination3 = new MockForwardFileDestination(dest3Dir);
    }

    @Test
    void testAdd_success() {
        final MultiForwardDestination multiForwardDestination = new MultiForwardDestination(List.of(
                mockForwardDestination1,
                mockForwardDestination2,
                mockForwardDestination3),
                new NumberedDirProvider(copiesDir),
                new CleanupDirQueue(() -> deletesDir));

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        Assertions.assertThat(copiesDir)
                .isEmptyDirectory();
        Assertions.assertThat(dest1Dir)
                .isEmptyDirectory();
        Assertions.assertThat(dest2Dir)
                .isEmptyDirectory();
        Assertions.assertThat(dest3Dir)
                .isEmptyDirectory();

        multiForwardDestination.add(source1);

        Assertions.assertThat(copiesDir)
                .isEmptyDirectory();

        Assertions.assertThat(dest1Dir)
                .isNotEmptyDirectory();
        Assertions.assertThat(dest2Dir)
                .isNotEmptyDirectory();
        Assertions.assertThat(dest3Dir)
                .isNotEmptyDirectory();

        final Snapshot dest1Snapshot = DirectorySnapshot.of(mockForwardDestination1.getAddedPaths().getFirst());
        final Snapshot dest2Snapshot = DirectorySnapshot.of(mockForwardDestination2.getAddedPaths().getFirst());
        final Snapshot dest3Snapshot = DirectorySnapshot.of(mockForwardDestination3.getAddedPaths().getFirst());

        Assertions.assertThat(dest1Snapshot)
                .isEqualTo(source1Snapshot);
        Assertions.assertThat(dest2Snapshot)
                .isEqualTo(source1Snapshot);
        Assertions.assertThat(dest3Snapshot)
                .isEqualTo(source1Snapshot);
    }

    @Test
    void testAdd_oneFails() {
//        final ForwardDestination badDest1 = Mockito.mock(ForwardDestination.class);
        final ForwardDestination badDest2 = Mockito.mock(ForwardDestination.class);

//        Mockito.doThrow(new RuntimeException("badDes1 error"))
//                .when(badDest1).add(Mockito.any());
        Mockito.doThrow(new RuntimeException("badDes2 error"))
                .when(badDest2).add(Mockito.any());

        final MultiForwardDestination multiForwardDestination = new MultiForwardDestination(List.of(
                mockForwardDestination1,
                badDest2,
                mockForwardDestination3),
                new NumberedDirProvider(copiesDir),
                new CleanupDirQueue(() -> deletesDir));

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        Assertions.assertThat(source1)
                .isDirectory();
        Assertions.assertThat(copiesDir)
                .isEmptyDirectory();
        Assertions.assertThat(dest1Dir)
                .isEmptyDirectory();
        Assertions.assertThat(dest2Dir)
                .isEmptyDirectory();
        Assertions.assertThat(dest3Dir)
                .isEmptyDirectory();

        Assertions.assertThatThrownBy(
                        () -> multiForwardDestination.add(source1))
                .isInstanceOf(RuntimeException.class);

        Assertions.assertThat(copiesDir)
                .isEmptyDirectory();

        Assertions.assertThat(dest1Dir)
                .isNotEmptyDirectory();
        Assertions.assertThat(dest3Dir)
                .isNotEmptyDirectory();

        // Source still exists because one dest failed
        Assertions.assertThat(source1)
                .isDirectory();

        final Snapshot dest1Snapshot = DirectorySnapshot.of(mockForwardDestination1.getAddedPaths().getFirst());
        final Snapshot dest3Snapshot = DirectorySnapshot.of(mockForwardDestination3.getAddedPaths().getFirst());

        Assertions.assertThat(dest1Snapshot)
                .isEqualTo(source1Snapshot);
        Assertions.assertThat(dest3Snapshot)
                .isEqualTo(source1Snapshot);
    }

    private Path createSourceDir(final int num) {
        return createSourceDir(num, null);
    }

    private Path createSourceDir(final int num,
                                 final Map<String, String> attrs) {
        final Path sourceDir = sourcesDir.resolve("source_" + num);
        FileUtil.ensureDirExists(sourceDir);
        Assertions.assertThat(sourceDir)
                .isDirectory()
                .exists();

        final FileGroup fileGroup = new FileGroup(sourceDir);
        fileGroup.items()
                .forEach(ThrowingConsumer.unchecked(FileUtil::touch));

        try {
            if (NullSafe.hasEntries(attrs)) {
                final Path meta = fileGroup.getMeta();
                final AttributeMap attributeMap = new AttributeMap(attrs);
                AttributeMapUtil.write(attributeMap, meta);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
    }
}
