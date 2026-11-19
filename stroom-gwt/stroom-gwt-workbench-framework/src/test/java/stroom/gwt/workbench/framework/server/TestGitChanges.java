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

package stroom.gwt.workbench.framework.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestGitChanges {

    private static final Path ROOT = Path.of("/repo");

    @Test
    void testParse() {
        final String porcelain = String.join("\0",
                " M src/Modified.java",
                "M  src/Staged.java",
                "MM src/Both.java",
                "?? src/Untracked.java",
                "A  src/Added.java",
                // Renames and copies are followed by the original path
                "R  src/Renamed.java",
                "MM src/OldLooksLikeAStatus.java",
                "C  src/Copied.java",
                "src/Original.java",
                " D src/Deleted.java",
                "?? src/With Space.java",
                "?? src/ Leading space.java",
                "?? src/Ünïcödé.java",
                "?? src/a -> b.java",
                "x",
                "") + "\0";
        final GitChanges.Changes changes = GitChanges.parse(ROOT, porcelain);
        assertThat(changes.getAdded()).containsExactly(
                ROOT.resolve("src/Untracked.java"),
                ROOT.resolve("src/Added.java"),
                ROOT.resolve("src/Copied.java"),
                ROOT.resolve("src/With Space.java"),
                ROOT.resolve("src/ Leading space.java"),
                ROOT.resolve("src/Ünïcödé.java"),
                ROOT.resolve("src/a -> b.java"));
        assertThat(changes.getModified()).containsExactly(
                ROOT.resolve("src/Modified.java"),
                ROOT.resolve("src/Staged.java"),
                ROOT.resolve("src/Both.java"),
                ROOT.resolve("src/Renamed.java"));
    }

    @Test
    void testParseEmpty() {
        final GitChanges.Changes changes = GitChanges.parse(ROOT, "");
        assertThat(changes.getAdded()).isEmpty();
        assertThat(changes.getModified()).isEmpty();
    }

    @Test
    void testFindInRepository(@TempDir final Path tempDir) throws IOException {
        GitTestUtil.init(tempDir);
        Files.writeString(tempDir.resolve("Modified.java"), "a");
        Files.writeString(tempDir.resolve("Unchanged.java"), "a");
        Files.writeString(tempDir.resolve("Old Name.java"), "a\nb\nc\nd\n");
        GitTestUtil.commitAll(tempDir);

        Files.writeString(tempDir.resolve("Modified.java"), "b");
        Files.createDirectories(tempDir.resolve("dir"));
        Files.writeString(tempDir.resolve("dir/New file ü.java"), "a");
        GitTestUtil.git(tempDir, "mv", "Old Name.java", "New Name.java");

        // Git gives real paths, so compare with the temporary directory's real path
        final Path root = tempDir.toRealPath();
        final GitChanges.Changes changes = new GitChanges(tempDir.resolve("dir")).find();
        assertThat(changes.getAdded()).containsExactly(root.resolve("dir/New file ü.java"));
        assertThat(changes.getModified())
                .containsExactlyInAnyOrder(root.resolve("Modified.java"), root.resolve("New Name.java"));
    }

    @Test
    void testFindOutsideRepository(@TempDir final Path tempDir) {
        // Not a git repository, so there are no changes rather than an error
        final GitChanges.Changes changes = new GitChanges(tempDir).find();
        assertThat(changes.getAdded()).isEmpty();
        assertThat(changes.getModified()).isEmpty();
    }
}
