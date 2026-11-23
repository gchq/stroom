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

package stroom.util.xml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSaxonPolicy {

    @TempDir
    Path tempDir;

    private Path allowedDir;
    private Path allowedFile;
    private Path outsideDir;
    private Path outsideFile;
    private SaxonPolicy policy;

    @BeforeEach
    void setUp() throws IOException {
        allowedDir = Files.createDirectories(tempDir.resolve("allowed"));
        allowedFile = Files.writeString(allowedDir.resolve("a.txt"), "a");
        outsideDir = Files.createDirectories(tempDir.resolve("outside"));
        outsideFile = Files.writeString(outsideDir.resolve("data.txt"), "content");
        policy = SaxonPolicy.allowing(List.of(allowedDir.toUri().toString()));
    }

    @Test
    void testAllowedFile() {
        assertThat(policy.isAllowed(allowedFile.toUri().toString())).isTrue();
        assertThat(policy.isAllowed(allowedDir.toUri().toString())).isTrue();
        assertThat(policy.isAllowed("file://localhost" + allowedFile)).isTrue();
        assertThat(policy.isAllowed(allowedDir.toUri() + "?recurse=yes")).isTrue();
    }

    @Test
    void testFileNotYetCreated() {
        assertThat(policy.isAllowed(allowedDir.resolve("later/b.txt").toUri().toString())).isTrue();
        assertThat(policy.isAllowed(outsideDir.resolve("later/b.txt").toUri().toString())).isFalse();
    }

    @Test
    void testOutsideRefused() {
        assertThat(policy.isAllowed(outsideFile.toUri().toString())).isFalse();
        assertThat(policy.isAllowed(tempDir.toUri().toString())).isFalse();
    }

    @Test
    void testWholeSegmentsOnly() throws IOException {
        final Path sibling = Files.createDirectories(tempDir.resolve("allowed2"));
        final Path siblingFile = Files.writeString(sibling.resolve("b.txt"), "b");
        assertThat(SaxonPolicy.allowing(List.of(allowedDir.toString())).isAllowed(siblingFile.toUri().toString()))
                .isFalse();
    }

    @Test
    void testParentSegmentRefused() {
        assertThat(policy.isAllowed(allowedDir.toUri() + "../outside/data.txt")).isFalse();
        assertThat(policy.isAllowed(allowedDir.toUri() + "%2e%2e/outside/data.txt")).isFalse();
        // Even when it would end up inside the allowed directory.
        assertThat(policy.isAllowed(allowedDir.toUri() + "x/../a.txt")).isFalse();
    }

    @Test
    void testSymbolicLinkOutRefused() throws IOException {
        final Path link = Files.createSymbolicLink(allowedDir.resolve("link.txt"), outsideFile);
        assertThat(policy.isAllowed(link.toUri().toString())).isFalse();
    }

    @Test
    void testSymbolicLinkThenParentRefused() throws IOException {
        // allowed/linkdir -> outside/sub, so allowed/linkdir/../data.txt is outside/data.txt to the OS.
        final Path sub = Files.createDirectories(outsideDir.resolve("sub"));
        final Path linkDir = Files.createSymbolicLink(allowedDir.resolve("linkdir"), sub);
        assertThat(policy.isAllowed(linkDir.toUri() + "../data.txt")).isFalse();
        assertThat(policy.isAllowed(linkDir.toUri() + "x.txt")).isFalse();
    }

    @Test
    void testAllowedDirectoryReachedThroughLink() throws IOException {
        // The allowed prefix is a link, as when a directory is mounted somewhere and linked to.
        final Path link = Files.createSymbolicLink(tempDir.resolve("lookups"), allowedDir);
        final SaxonPolicy linkPolicy = SaxonPolicy.allowing(List.of(link.toString()));
        assertThat(linkPolicy.isAllowed(link.resolve("a.txt").toUri().toString())).isTrue();
        assertThat(linkPolicy.isAllowed(allowedFile.toUri().toString())).isTrue();
        assertThat(linkPolicy.isAllowed(outsideFile.toUri().toString())).isFalse();
    }

    @Test
    void testAllowedDirectoryCreatedLater() throws IOException {
        final Path later = tempDir.resolve("later");
        final SaxonPolicy laterPolicy = SaxonPolicy.allowing(List.of(later.toString()));

        // Links are resolved when checking, so a directory made (or linked) after the policy is seen.
        Files.createSymbolicLink(later, allowedDir);
        assertThat(laterPolicy.isAllowed(later.resolve("a.txt").toUri().toString())).isTrue();
        assertThat(laterPolicy.isAllowed(allowedFile.toUri().toString())).isTrue();
    }

    @Test
    void testBrokenLinkRefused() throws IOException {
        final Path link = Files.createSymbolicLink(allowedDir.resolve("broken"), tempDir.resolve("missing"));
        assertThat(policy.isAllowed(link.toUri().toString())).isFalse();
    }

    @Test
    void testResolveAllowedDirectory() throws IOException {
        assertThat(policy.resolveAllowedDirectory(allowedDir.toUri().toString()))
                .isEqualTo(allowedDir.toRealPath());
        assertThat(policy.resolveAllowedDirectory(allowedFile.toUri().toString())).isNull();
        assertThat(policy.resolveAllowedDirectory(outsideDir.toUri().toString())).isNull();
        assertThat(policy.resolveAllowedDirectory(null)).isNull();
    }

    @Test
    void testNotFileUris() {
        assertThat(policy.isAllowed(null)).isFalse();
        assertThat(policy.isAllowed("")).isFalse();
        assertThat(policy.isAllowed("not a uri")).isFalse();
        assertThat(policy.isAllowed("http://127.0.0.1" + allowedFile)).isFalse();
        assertThat(policy.isAllowed("file://remote-host" + allowedFile)).isFalse();
        assertThat(policy.isAllowed("relative/a.txt")).isFalse();
    }

    @Test
    void testLockedAllowsNothing() {
        assertThat(SaxonPolicy.LOCKED.isAllowed(allowedFile.toUri().toString())).isFalse();
    }

    @Test
    void testUnusablePrefixesIgnored() {
        assertThat(SaxonPolicy.allowing(null)).isEqualTo(SaxonPolicy.LOCKED);
        assertThat(SaxonPolicy.allowing(Arrays.asList(null, "", "  ", "relative/dir", "http://example.com/",
                "https://example.com/", "file://remote-host/share/", allowedDir + "/../outside")))
                .isEqualTo(SaxonPolicy.LOCKED);
    }
}
