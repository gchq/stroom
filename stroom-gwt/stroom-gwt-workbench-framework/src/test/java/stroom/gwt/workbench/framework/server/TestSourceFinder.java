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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSourceFinder {

    @TempDir
    Path tempDir;

    private Path root1;
    private Path root2;
    private SourceFinder sourceFinder;

    @BeforeEach
    void setUp() throws IOException {
        root1 = tempDir.resolve("one/src/main/java");
        root2 = tempDir.resolve("two/src/main/java");
        write(root1, "a/client/Button.java", """
                package a.client;

                import a.shared.Model;
                import java.util.List;

                public class Button {
                }
                """);
        write(root1, "a/client/ButtonStories.java", "public final class ButtonStories {}");
        write(root1, "a/client/Helper.java", "class Helper {}");
        write(root1, "a/shared/Model.java", "public class Model {}");
        write(root2, "b/client/TickBox.java", "public final class TickBox {}");
        write(root2, "b/client/widgets/MenuStories.java", """
                public final class MenuStories {
                    static void addTo(StoryRegistry registry) {
                        registry.component("Widgets/Menu", MenuStories.class)
                                .story("Default", context -> null)
                                .story("WithIcons", context -> null);
                    }
                }
                """);
        sourceFinder = new SourceFinder(List.of(root1, root2));
    }

    @Test
    void testFindFile() {
        assertThat(sourceFinder.findFile("a.client.Button")).contains(root1.resolve("a/client/Button.java"));
        assertThat(sourceFinder.findFile("b.client.TickBox")).contains(root2.resolve("b/client/TickBox.java"));
        // Nested classes are in their outer class's file
        assertThat(sourceFinder.findFile("a.client.Button$Inner"))
                .contains(root1.resolve("a/client/Button.java"));
    }

    @Test
    void testFindFileInvalid() {
        assertThat(sourceFinder.findFile(null)).isEmpty();
        assertThat(sourceFinder.findFile("a.client.Missing")).isEmpty();
        assertThat(sourceFinder.findFile("../../etc/passwd")).isEmpty();
        assertThat(sourceFinder.findFile("a/client/Button")).isEmpty();
    }

    @Test
    void testToClassName() {
        assertThat(sourceFinder.toClassName(root2.resolve("b/client/TickBox.java"))).contains("b.client.TickBox");
        assertThat(sourceFinder.toClassName(tempDir.resolve("Other.java"))).isEmpty();
        assertThat(sourceFinder.toClassName(root1.resolve("a/client/readme.txt"))).isEmpty();
    }

    @Test
    void testFindStoryLine() {
        final Path file = root2.resolve("b/client/widgets/MenuStories.java");
        assertThat(sourceFinder.findStoryLine(file, "widgets-menu--default")).isEqualTo(4);
        assertThat(sourceFinder.findStoryLine(file, "widgets-menu--with-icons")).isEqualTo(5);
        assertThat(sourceFinder.findStoryLine(file, "widgets-menu--missing")).isEqualTo(1);
        assertThat(sourceFinder.findStoryLine(file, "widgets-menu")).isEqualTo(1);
        assertThat(sourceFinder.findStoryLine(file, null)).isEqualTo(1);
    }

    @Test
    void testFindStoryLineWithNameOnNextLine() throws IOException {
        final Path file = write(root2, "b/client/widgets/TabStories.java", """
                public final class TabStories {
                    static void addTo(StoryRegistry registry) {
                        registry.component("Widgets/Tab", TabStories.class)
                                .story(
                                        "Default",
                                        context -> null)
                                .story("Closable", context -> null);
                    }
                }
                """);
        assertThat(sourceFinder.findStoryLine(file, "widgets-tab--default")).isEqualTo(4);
        assertThat(sourceFinder.findStoryLine(file, "widgets-tab--closable")).isEqualTo(7);
    }

    @Test
    void testFindComponentsWithNonUtf8File() throws IOException {
        final Path file = root2.resolve("b/client/Latin.java");
        // 'é' in ISO-8859-1, which isn't valid UTF-8
        Files.write(file, "// Café\npublic class Latin {}\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(sourceFinder.findComponents("", 50))
                .extracting(SourceFinder.Component::getClassName)
                .containsExactly("a.client.Button", "b.client.Latin", "b.client.TickBox");
        assertThat(SourceFinder.readSource(file)).startsWith("// Café\n");
    }

    @Test
    void testFindFileRejectsSymbolicLinkOutOfRoot() throws IOException {
        final Path outside = write(tempDir, "outside/Secret.java", "public class Secret {}");
        final Path inside = write(root1, "a/client/Inside.java", "public class Inside {}");
        createSymbolicLinkOrSkip(root1.resolve("a/client/Secret.java"), outside);
        createSymbolicLinkOrSkip(root1.resolve("a/client/Linked.java"), inside);

        assertThat(sourceFinder.findFile("a.client.Secret")).isEmpty();
        // A link within the root is fine
        assertThat(sourceFinder.findFile("a.client.Linked")).contains(root1.resolve("a/client/Linked.java"));
        assertThat(sourceFinder.findComponents("secret", 50)).isEmpty();
    }

    @Test
    void testFindComponents() {
        // Only public classes in client packages, without stories
        assertThat(sourceFinder.findComponents("", 50))
                .extracting(SourceFinder.Component::getClassName)
                .containsExactly("a.client.Button", "b.client.TickBox");
        assertThat(sourceFinder.findComponents("tick", 50))
                .extracting(SourceFinder.Component::getPath)
                .containsExactly("b/client/TickBox.java");
        assertThat(sourceFinder.findComponents(null, 1)).hasSize(1);
        assertThat(sourceFinder.findComponents("nothing", 50)).isEmpty();
    }

    @Test
    void testFindComponentsIgnoresMissingRoot() {
        final SourceFinder finder = new SourceFinder(List.of(tempDir.resolve("missing"), root2));
        assertThat(finder.findComponents("", 50))
                .extracting(SourceFinder.Component::getSimpleName)
                .containsExactly("TickBox");
    }

    @Test
    void testFindImports() {
        assertThat(sourceFinder.findImports(root1.resolve("a/client/Button.java")))
                .containsExactly("a.shared.Model", "java.util.List");
    }

    private static Path write(final Path root, final String path, final String content) throws IOException {
        final Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static void createSymbolicLinkOrSkip(final Path link, final Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (final IOException | UnsupportedOperationException e) {
            Assumptions.abort("Can't create symbolic links: " + e);
        }
    }
}
