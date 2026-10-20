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

package stroom.credentials.impl;

import stroom.util.io.PathCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TestKeyStoreFilePathChecker {

    @TempDir
    Path tempDir;

    @Mock
    private PathCreator pathCreator;

    private Path allowedDir;
    private List<String> allowedDirs;
    private KeyStoreFilePathChecker checker;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(pathCreator.toAppPath(anyString()))
                .thenAnswer(invocation -> Path.of(invocation.<String>getArgument(0)));
        allowedDir = Files.createDirectories(tempDir.resolve("certs"));
        allowedDirs = List.of(allowedDir.toString());
        checker = new KeyStoreFilePathChecker(pathCreator,
                () -> new CredentialsConfig(null, null, allowedDirs));
    }

    @Test
    void testFileInAllowedDir() throws IOException {
        final Path file = Files.createFile(allowedDir.resolve("client.p12"));

        assertThat(checker.check(file.toString()))
                .isEqualTo(file.toRealPath());
    }

    @Test
    void testFileInSubDirOfAllowedDir() throws IOException {
        final Path file = Files.createFile(Files.createDirectories(allowedDir.resolve("a/b")).resolve("client.p12"));

        assertThat(checker.check(file.toString()))
                .isEqualTo(file.toRealPath());
    }

    /// Key managers such as cert-manager mount key stores through links within the mount
    /// directory. The current target of the link must be returned, so that what gets opened is
    /// the file that was checked, and a link moved to a new file is followed on the next check.
    @Test
    void testLinkWithinAllowedDir() throws IOException {
        final Path firstTarget = Files.createFile(
                Files.createDirectories(allowedDir.resolve("..2026_10_19")).resolve("client.p12"));
        final Path link = Files.createSymbolicLink(allowedDir.resolve("client.p12"), firstTarget);

        assertThat(checker.check(link.toString()))
                .isEqualTo(firstTarget.toRealPath());

        // The key manager rotates the key store by moving the link to a new file
        final Path secondTarget = Files.createFile(
                Files.createDirectories(allowedDir.resolve("..2026_11_19")).resolve("client.p12"));
        Files.delete(link);
        Files.createSymbolicLink(link, secondTarget);

        assertThat(checker.check(link.toString()))
                .isEqualTo(secondTarget.toRealPath());
    }

    @Test
    void testDisabledWhenNoAllowedDirs() throws IOException {
        final Path file = Files.createFile(allowedDir.resolve("client.p12"));
        allowedDirs = List.of();

        assertThatThrownBy(() -> checker.check(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not enabled")
                .hasMessageContaining(KeyStoreFilePathChecker.CONFIG_PROPERTY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testBlankPath(final String path) {
        assertThatThrownBy(() -> checker.check(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be given");
    }

    @Test
    void testRelativePath() {
        assertThatThrownBy(() -> checker.check("certs/client.p12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void testFileOutsideAllowedDir() throws IOException {
        final Path file = Files.createFile(tempDir.resolve("server.p12"));

        assertThatThrownBy(() -> checker.check(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in a directory allowed");
    }

    /// The result must be the same whether or not a file outside the allowed dirs exists, so it
    /// can't be used to find out what is on the server.
    @Test
    void testMissingFileOutsideAllowedDirGivesSameError() {
        assertThatThrownBy(() -> checker.check(tempDir.resolve("missing.p12").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in a directory allowed");
    }

    @Test
    void testDirWithSamePrefixIsNotAllowed() throws IOException {
        final Path file = Files.createFile(Files.createDirectories(tempDir.resolve("certs-other")).resolve("x.p12"));

        assertThatThrownBy(() -> checker.check(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in a directory allowed");
    }

    @Test
    void testParentTraversalIsNotAllowed() throws IOException {
        Files.createFile(tempDir.resolve("server.p12"));

        assertThatThrownBy(() -> checker.check(allowedDir.resolve("../server.p12").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in a directory allowed");
    }

    @Test
    void testLinkOutOfAllowedDirIsNotAllowed() throws IOException {
        final Path outside = Files.createFile(tempDir.resolve("server.p12"));
        final Path link = Files.createSymbolicLink(allowedDir.resolve("client.p12"), outside);

        assertThatThrownBy(() -> checker.check(link.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in a directory allowed");
    }

    @Test
    void testAllowedDirItselfIsNotAFile() {
        assertThatThrownBy(() -> checker.check(allowedDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in a directory allowed");
    }

    @Test
    void testDirInAllowedDirIsNotAFile() throws IOException {
        final Path dir = Files.createDirectories(allowedDir.resolve("sub"));

        assertThatThrownBy(() -> checker.check(dir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a file");
    }

    @Test
    void testPathTooLong() {
        final String path = allowedDir.resolve("a".repeat(KeyStoreFilePathChecker.MAX_PATH_LENGTH)).toString();

        assertThatThrownBy(() -> checker.check(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no more than " + KeyStoreFilePathChecker.MAX_PATH_LENGTH);
    }

    @Test
    void testMissingFileInAllowedDir() {
        assertThatThrownBy(() -> checker.check(allowedDir.resolve("missing.p12").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }
}
