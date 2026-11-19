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

package stroom.lmdb2;

import stroom.lmdb.LmdbLibrary;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestLmdbEnvDirFactory {

    @Mock
    private LmdbLibrary mockLmdbLibrary;
    @Mock
    private PathCreator mockPathCreator;

    @TempDir
    private Path tempDir;

    @Test
    void testBuild_safeSubDir() {
        final Path localDir = tempDir.resolve("lmdb");

        final LmdbEnvDir lmdbEnvDir = buildEnvDir(localDir, "queryKey_table-ABC123_uuid");

        assertThat(lmdbEnvDir.getEnvDir())
                .isEqualTo(localDir.resolve("queryKey_table-ABC123_uuid"));
        assertThat(lmdbEnvDir.getEnvDir())
                .isDirectory();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "queryKey_/../../escaped_uuid",
            "queryKey_\\..\\..\\escaped_uuid",
            "/escaped",
            "..",
            "."})
    void testBuild_subDirCannotEscapeLocalDir(final String subDir) throws Exception {
        final Path localDir = tempDir.resolve("lmdb");

        final LmdbEnvDir lmdbEnvDir = buildEnvDir(localDir, subDir);

        final Path envDir = lmdbEnvDir.getEnvDir();
        assertThat(envDir.getParent())
                .isEqualTo(localDir);
        assertThat(envDir.toRealPath().getParent())
                .isEqualTo(localDir.toRealPath());

        // Nothing should have been created anywhere other than in the local dir.
        final List<Path> tempDirChildren;
        try (final Stream<Path> stream = Files.list(tempDir)) {
            tempDirChildren = stream.toList();
        }
        assertThat(tempDirChildren)
                .containsExactly(localDir);
    }

    private LmdbEnvDir buildEnvDir(final Path localDir, final String subDir) {
        return new LmdbEnvDirFactory(mockLmdbLibrary, mockPathCreator)
                .builder()
                .localDir(localDir)
                .subDir(subDir)
                .build();
    }
}
