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

package stroom.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestCommonDirSetup {

    @Test
    void theDirectoriesAreDeletedWhenTheJvmExits() throws IOException, InterruptedException {
        // Seen: a test JVM's temp and home directories were left behind, 19 GB of them in /tmp after a day of
        // test runs.
        final Process jvm = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), SetUpAndExit.class.getName())
                .redirectErrorStream(true)
                .start();
        assertThat(jvm.waitFor(1, TimeUnit.MINUTES)).isTrue();
        final List<String> made = new String(jvm.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .filter(line -> line.startsWith("dir="))
                .map(line -> line.substring("dir=".length()))
                .toList();

        assertThat(jvm.exitValue()).isZero();
        assertThat(made).hasSize(2);
        assertThat(made).allSatisfy(dir -> assertThat(Path.of(dir)).doesNotExist());
    }

    @Test
    void deletingTakesTheDirectoriesAndWhatIsInThem(@TempDir final Path tempDir) throws IOException {
        final Path home = Files.createDirectories(tempDir.resolve("home/content-cache/pack"));
        Files.writeString(home.resolve("file.txt"), "x");
        final Path temp = Files.createDirectories(tempDir.resolve("temp"));

        CommonDirSetup.delete(tempDir.resolve("home"), temp, tempDir.resolve("never-made"));

        assertThat(tempDir.resolve("home")).doesNotExist();
        assertThat(temp).doesNotExist();
    }

    // --------------------------------------------------------------------------------

    /// A JVM that sets Stroom's directories up, says where they are, and exits.
    static final class SetUpAndExit {

        public static void main(final String[] args) throws IOException {
            CommonDirSetup.setup();
            final Path temp = Path.of(System.getProperty(TempDirProviderImpl.PROP_STROOM_TEMP));
            final Path home = Path.of(System.getProperty(HomeDirProviderImpl.PROP_STROOM_HOME));
            // Something in each, as a test leaves.
            Files.writeString(temp.resolve("file.txt"), "x");
            Files.createDirectories(home.resolve("content-cache"));
            System.out.println("dir=" + temp);
            System.out.println("dir=" + home);
        }
    }
}
