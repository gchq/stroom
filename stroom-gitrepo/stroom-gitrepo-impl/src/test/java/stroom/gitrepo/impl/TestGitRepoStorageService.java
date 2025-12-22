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

package stroom.gitrepo.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class to test the GitRepoStorageService
 */
public class TestGitRepoStorageService {

    @Test
    public void testAddDirectoryToPath() throws IOException {

        // Create a test directory
        final Path fullPath = Paths.get("/tmp/TestGitRepoStorageService/one/two/three");
        Files.createDirectories(fullPath);

        final Path parent = Paths.get("/tmp/TestGitRepoStorageService/one");
        final Path subdir = Paths.get("two/three");
        Path resolved = GitRepoStorageServiceImpl.addDirectoryToPath(parent, subdir);

        // Ensure that the test is running
        assertThat(resolved).isEqualTo(fullPath);

        try {
            final Path badDir = Paths.get("..");
            resolved = GitRepoStorageServiceImpl.addDirectoryToPath(parent, badDir);
            assertThat(resolved)
                    .as("Shouldn't get here - exception should be thrown")
                    .isEqualTo(fullPath);
        } catch (final IOException e) {
            // Ok - exception thrown
            System.err.println("Correct behaviour - exception thrown: " + e);
        }

    }
}
