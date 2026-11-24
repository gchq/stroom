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

package stroom.test;

import stroom.util.io.FileUtil;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestGitRepoCache {

    private static final PersonIdent PERSON = new PersonIdent("Test", "test@example.com");

    @TempDir
    private Path tempDir;

    private Path sourceDir;
    private String sourceUrl;
    private GitRepoCache gitRepoCache;

    @BeforeEach
    void setUp() throws Exception {
        sourceDir = tempDir.resolve("source");
        sourceUrl = sourceDir.toUri().toString();
        gitRepoCache = new GitRepoCache(tempDir.resolve("cache"));
        try (final Git git = Git.init().setDirectory(sourceDir.toFile()).setInitialBranch("main").call()) {
            commit(git, "file1.txt");
        }
    }

    @Test
    void testFirstUse_clonesIntoVersionDir() throws Exception {
        final Path versionDir = gitRepoCache.getRepo(sourceUrl);

        assertThat(versionDir.resolve("file1.txt"))
                .exists();
        // Named after the hash of the remote's branches and tags
        assertThat(versionDir.getFileName().toString())
                .matches("[0-9a-f]{64}");
        assertThat(listEntries(versionDir.getParent()))
                .containsExactlyInAnyOrder(versionDir.getFileName().toString(), GitRepoCache.LOCK_FILE_NAME);
    }

    @Test
    void testRemoteUnchanged_reusesVersion() throws Exception {
        final Path versionDir1 = gitRepoCache.getRepo(sourceUrl);
        // Only possible to see this again if it was not cloned again
        Files.writeString(versionDir1.resolve("marker"), "marker");

        final Path versionDir2 = gitRepoCache.getRepo(sourceUrl);

        assertThat(versionDir2)
                .isEqualTo(versionDir1);
        assertThat(versionDir2.resolve("marker"))
                .exists();
    }

    @Test
    void testNewCommit_downloadsNewVersion() throws Exception {
        final Path versionDir1 = gitRepoCache.getRepo(sourceUrl);
        try (final Git git = Git.open(sourceDir.toFile())) {
            commit(git, "file2.txt");
        }

        final Path versionDir2 = gitRepoCache.getRepo(sourceUrl);

        assertThat(versionDir2)
                .isNotEqualTo(versionDir1);
        assertThat(versionDir2.resolve("file2.txt"))
                .exists();
        // The old version is untouched, as another JVM may still be using it
        assertThat(versionDir1.resolve("file1.txt"))
                .exists();
        assertThat(versionDir1.resolve("file2.txt"))
                .doesNotExist();
    }

    @Test
    void testNewTag_downloadsNewVersion() throws Exception {
        final Path versionDir1 = gitRepoCache.getRepo(sourceUrl);
        try (final Git git = Git.open(sourceDir.toFile())) {
            git.tag().setName("v1.0").setAnnotated(true).setMessage("v1.0").setTagger(PERSON).setSigned(false)
                    .call();
        }

        final Path versionDir2 = gitRepoCache.getRepo(sourceUrl);

        assertThat(versionDir2)
                .isNotEqualTo(versionDir1);
        try (final Git git = Git.open(versionDir2.toFile())) {
            assertThat(git.getRepository().exactRef("refs/tags/v1.0"))
                    .isNotNull();
        }
    }

    @Test
    void testTagNotOnAnyBranch_isClonedSoVersionMatchesRemote() throws Exception {
        // ls-remote lists every tag, including one on a commit that is on no branch. If the clone did not
        // have it too, the clone would never match the remote and every use would download it again.
        try (final Git git = Git.open(sourceDir.toFile())) {
            git.checkout().setCreateBranch(true).setName("temp").call();
            commit(git, "temp.txt");
            git.tag().setName("orphan").setAnnotated(true).setMessage("orphan").setTagger(PERSON).setSigned(false)
                    .call();
            git.checkout().setName("main").call();
            git.branchDelete().setBranchNames("temp").setForce(true).call();
        }

        final Path versionDir1 = gitRepoCache.getRepo(sourceUrl);
        Files.writeString(versionDir1.resolve("marker"), "marker");
        final Path versionDir2 = gitRepoCache.getRepo(sourceUrl);

        assertThat(versionDir2)
                .isEqualTo(versionDir1);
        assertThat(versionDir2.resolve("marker"))
                .exists();
    }

    @Test
    void testRemoteUnreachable_usesCachedVersion() throws Exception {
        final Path versionDir1 = gitRepoCache.getRepo(sourceUrl);
        FileUtil.deleteDir(sourceDir);

        final Path versionDir2 = gitRepoCache.getRepo(sourceUrl);

        assertThat(versionDir2)
                .isEqualTo(versionDir1);
    }

    @Test
    void testRemoteUnreachable_noCache() {
        final String missingUrl = tempDir.resolve("missing").toUri().toString();

        assertThatThrownBy(() -> gitRepoCache.getRepo(missingUrl))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no cached copy");
    }

    @Test
    void testUnusedEntriesDeleted() throws Exception {
        final Path versionDir1 = gitRepoCache.getRepo(sourceUrl);
        final Path repoDir = versionDir1.getParent();
        final Instant longAgo = Instant.now().minus(GitRepoCache.RETENTION).minus(Duration.ofHours(1));

        // An unused old version and an abandoned temporary directory from an interrupted download
        final Path unusedVersionDir = Files.createDirectory(repoDir.resolve("0".repeat(64)));
        final Path abandonedTempDir = Files.createDirectory(repoDir.resolve(GitRepoCache.TEMP_DIR_PREFIX + "1"));
        Files.setLastModifiedTime(unusedVersionDir, FileTime.from(longAgo));
        Files.setLastModifiedTime(abandonedTempDir, FileTime.from(longAgo));

        try (final Git git = Git.open(sourceDir.toFile())) {
            commit(git, "file2.txt");
        }
        final Path versionDir2 = gitRepoCache.getRepo(sourceUrl);

        assertThat(unusedVersionDir)
                .doesNotExist();
        assertThat(abandonedTempDir)
                .doesNotExist();
        // Recently used, so may still be in use by another JVM
        assertThat(versionDir1)
                .exists();
        assertThat(versionDir2)
                .exists();
    }

    @Test
    void testUse_updatesModifiedTime() throws Exception {
        final Path versionDir = gitRepoCache.getRepo(sourceUrl);
        final Instant longAgo = Instant.now().minus(Duration.ofDays(10));
        Files.setLastModifiedTime(versionDir, FileTime.from(longAgo));

        gitRepoCache.getRepo(sourceUrl);

        assertThat(Files.getLastModifiedTime(versionDir).toInstant())
                .isAfter(longAgo.plus(Duration.ofDays(9)));
    }

    @Test
    void testConcurrentUse_downloadsOnce() throws Exception {
        final int threadCount = 8;
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {
            final CountDownLatch startLatch = new CountDownLatch(1);
            final List<Future<Path>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    startLatch.await();
                    return gitRepoCache.getRepo(sourceUrl);
                }));
            }
            startLatch.countDown();

            final List<Path> versionDirs = new ArrayList<>();
            for (final Future<Path> future : futures) {
                versionDirs.add(future.get());
            }

            assertThat(versionDirs)
                    .hasSize(threadCount)
                    .containsOnly(versionDirs.getFirst());
            assertThat(listEntries(versionDirs.getFirst().getParent()))
                    .containsExactlyInAnyOrder(
                            versionDirs.getFirst().getFileName().toString(),
                            GitRepoCache.LOCK_FILE_NAME);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static void commit(final Git git, final String fileName) throws Exception {
        Files.writeString(git.getRepository().getWorkTree().toPath().resolve(fileName), fileName);
        git.add().addFilepattern(fileName).call();
        git.commit().setMessage("Add " + fileName).setAuthor(PERSON).setCommitter(PERSON).setSign(false).call();
    }

    private static List<String> listEntries(final Path dir) throws IOException {
        try (final Stream<Path> entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        }
    }
}
