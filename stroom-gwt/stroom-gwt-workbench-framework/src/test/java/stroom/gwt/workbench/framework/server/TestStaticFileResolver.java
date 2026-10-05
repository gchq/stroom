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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStaticFileResolver {

    @TempDir
    Path tempDir;

    private Path root1;
    private Path root2;
    private StaticFileResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        root1 = Files.createDirectories(tempDir.resolve("root1"));
        root2 = Files.createDirectories(tempDir.resolve("root2"));
        Files.writeString(root1.resolve("index.html"), "root1 index");
        Files.writeString(root2.resolve("index.html"), "root2 index");
        Files.createDirectories(root2.resolve("ui/css"));
        Files.writeString(root2.resolve("ui/css/app.css"), "css");
        Files.writeString(tempDir.resolve("secret.txt"), "secret");
        resolver = new StaticFileResolver(List.of(root1, root2));
    }

    @Test
    void testResolve_firstRootWins() {
        assertThat(resolver.resolve("/index.html")).contains(root1.resolve("index.html"));
    }

    @Test
    void testResolve_fallsBackToLaterRoots() {
        assertThat(resolver.resolve("/ui/css/app.css")).contains(root2.resolve("ui/css/app.css"));
    }

    @Test
    void testResolve_relativeAndRepeatedSlashes() {
        assertThat(resolver.resolve("ui/css/app.css")).contains(root2.resolve("ui/css/app.css"));
        assertThat(resolver.resolve("//ui/css/app.css")).contains(root2.resolve("ui/css/app.css"));
    }

    @Test
    void testResolve_missing() {
        assertThat(resolver.resolve("/missing.html")).isEmpty();
    }

    @Test
    void testResolve_directoryIsNotAFile() {
        assertThat(resolver.resolve("/ui/css")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/../secret.txt",
            "/ui/../../secret.txt",
            "../secret.txt",
            "/ui/css/../../../secret.txt",
    })
    void testResolve_traversalIsBlocked(final String path) {
        assertThat(resolver.resolve(path)).isEmpty();
    }

    @Test
    void testResolve_symbolicLinkOutOfRootIsBlocked() throws IOException {
        createSymbolicLinkOrSkip(root2.resolve("secret.txt"), tempDir.resolve("secret.txt"));
        createSymbolicLinkOrSkip(root2.resolve("linked"), tempDir);
        createSymbolicLinkOrSkip(root2.resolve("app.css"), root2.resolve("ui/css/app.css"));

        assertThat(resolver.resolve("/secret.txt")).isEmpty();
        assertThat(resolver.resolve("/linked/secret.txt")).isEmpty();
        // A link within the root is fine
        assertThat(resolver.resolve("/app.css")).contains(root2.resolve("app.css"));
    }

    @Test
    void testResolve_rootThatIsASymbolicLink() throws IOException {
        final Path link = tempDir.resolve("link");
        createSymbolicLinkOrSkip(link, root2);
        assertThat(new StaticFileResolver(List.of(link)).resolve("/ui/css/app.css"))
                .contains(link.resolve("ui/css/app.css"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/", "  ", "/index.html\0.css"})
    void testResolve_invalid(final String path) {
        assertThat(resolver.resolve(path)).isEmpty();
    }

    @Test
    void testRootsAreNormalised() {
        final StaticFileResolver resolver2 = new StaticFileResolver(List.of(root1.resolve("../root2")));
        assertThat(resolver2.getRoots()).containsExactly(root2.toAbsolutePath().normalize());
        assertThat(resolver2.resolve("/ui/css/app.css")).isPresent();
    }

    @Test
    void testNullRoots() {
        assertThatThrownBy(() -> new StaticFileResolver(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static void createSymbolicLinkOrSkip(final Path link, final Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (final IOException | UnsupportedOperationException e) {
            Assumptions.abort("Can't create symbolic links: " + e);
        }
    }
}
