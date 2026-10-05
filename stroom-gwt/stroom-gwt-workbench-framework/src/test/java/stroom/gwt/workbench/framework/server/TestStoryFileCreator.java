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
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoryFileCreator {

    private static final String ALL_STORIES = """
            package app.client;

            import app.client.widgets.ButtonStories;
            import lib.StoryRegistry;

            public final class AllStories {

                public static StoryRegistry create() {
                    final StoryRegistry registry = new StoryRegistry();
                    ButtonStories.addTo(registry);
                    return registry;
                }
            }
            """;

    @TempDir
    Path tempDir;

    private Path allStories;
    private StoryFileCreator creator;

    @BeforeEach
    void setUp() throws IOException {
        allStories = tempDir.resolve("app/client/AllStories.java");
        Files.createDirectories(allStories.getParent());
        Files.writeString(allStories, ALL_STORIES);
        creator = new StoryFileCreator(tempDir, "app.client.widgets", allStories);
    }

    @Test
    void testCreate() throws IOException {
        final StoryFileCreator.Result result = creator.create("stroom.widget.tickbox.client.view.TickBox");

        final Path expectedFile = tempDir.resolve("app/client/widgets/TickBoxStories.java");
        assertThat(result.getFile()).isEqualTo(expectedFile);
        assertThat(result.getStoryId()).isEqualTo("widgets-tickbox--default");

        final String source = Files.readString(expectedFile);
        assertThat(source)
                .startsWith("/*\n * Copyright ")
                .contains("package app.client.widgets;\n\n"
                          + "import stroom.gwt.workbench.framework.client.story.StoryRegistry;\n"
                          + "import stroom.widget.tickbox.client.view.TickBox;\n\n"
                          + "/// Stories for [TickBox].")
                .contains("public final class TickBoxStories {")
                .contains("registry.component(\"Widgets/TickBox\", TickBoxStories.class)")
                .contains(".story(\"Default\", context -> new TickBox());");

        final String registry = Files.readString(allStories);
        assertThat(registry)
                .contains("import app.client.widgets.ButtonStories;\n"
                          + "import app.client.widgets.TickBoxStories;\n"
                          + "import lib.StoryRegistry;\n")
                .contains("        ButtonStories.addTo(registry);\n"
                          + "        TickBoxStories.addTo(registry);\n"
                          + "        return registry;");
    }

    @Test
    void testCreateInSamePackageDoesNotImport() throws IOException {
        final StoryFileCreator samePackage = new StoryFileCreator(tempDir, "app.client", allStories);
        samePackage.create("x.Menu");
        assertThat(Files.readString(allStories))
                .doesNotContain("import app.client.MenuStories;")
                .contains("MenuStories.addTo(registry);");
    }

    @Test
    void testCreateExisting() throws IOException {
        creator.create("x.Menu");
        assertThatThrownBy(() -> creator.create("y.Menu"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MenuStories already exists");
    }

    @Test
    void testCreateInvalidClassName() {
        assertThatThrownBy(() -> creator.create("../Evil"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCreateWithoutMarker() throws IOException {
        Files.writeString(allStories, "package app.client;\n\npublic final class AllStories {}\n");
        assertThatThrownBy(() -> creator.create("x.Menu"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("return registry;");

        // No stray file is left behind to stop a retry
        assertThat(tempDir.resolve("app/client/widgets/MenuStories.java")).doesNotExist();
        Files.writeString(allStories, ALL_STORIES);
        assertThat(creator.create("x.Menu").getFile()).exists();
    }

    @Test
    void testCreateRemovesStoriesIfRegisteringFails() throws IOException {
        try {
            Files.setPosixFilePermissions(allStories, PosixFilePermissions.fromString("r--r--r--"));
        } catch (final UnsupportedOperationException e) {
            Assumptions.abort("No POSIX file permissions");
        }
        Assumptions.assumeFalse(Files.isWritable(allStories), "Running as a user that can write anything");

        assertThatThrownBy(() -> creator.create("x.Menu")).isInstanceOf(IOException.class);
        assertThat(tempDir.resolve("app/client/widgets/MenuStories.java")).doesNotExist();
        assertThat(allStories).hasContent(ALL_STORIES);
    }

    @Test
    void testCreateConcurrently() throws Exception {
        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<Boolean>> results = new ArrayList<>();
        try (final ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        creator.create("x.Menu");
                        return true;
                    } catch (final IllegalStateException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int created = 0;
            for (final Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        }
        final String registry = Files.readString(allStories);
        assertThat(registry.split("MenuStories.addTo", -1)).hasSize(2);
        assertThat(registry.split("import app.client.widgets.MenuStories;", -1)).hasSize(2);
    }

    @Test
    void testCreateForComponentWithoutNoArgConstructor() throws IOException {
        final Path component = tempDir.resolve("Dialog.java");
        Files.writeString(component, """
                package x.client;

                public class Dialog {
                    public Dialog(final String title) {
                    }
                }
                """);
        creator.create("x.client.Dialog", component);
        assertThat(Files.readString(tempDir.resolve("app/client/widgets/DialogStories.java")))
                .contains("import stroom.gwt.workbench.framework.client.story.StoryRegistry;\n\n"
                          + "import com.google.gwt.user.client.ui.Label;\n\n"
                          + "/// Stories for [x.client.Dialog].")
                .doesNotContain("import x.client.Dialog;")
                .contains(".story(\"Default\", context -> new Label(\"Construct a Dialog here.\"));");
    }

    @Test
    void testCreateForComponentWithNoArgConstructor() throws IOException {
        final Path component = tempDir.resolve("Menu.java");
        Files.writeString(component, "package x;\n\npublic class Menu {\n    public Menu() {\n    }\n}\n");
        creator.create("x.Menu", component);
        assertThat(Files.readString(tempDir.resolve("app/client/widgets/MenuStories.java")))
                .contains("import stroom.gwt.workbench.framework.client.story.StoryRegistry;\n\nimport x.Menu;\n\n")
                .contains("context -> new Menu()");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "public class Menu {}|true",
            "public class Menu {\\n    public Menu() {}\\n}|true",
            "public class Menu {\\n    @Inject\\n    public Menu() {}\\n    public Menu(int a) {}\\n}|true",
            "public class Menu {\\n    public Menu(final int a) {}\\n}|false",
            "public class Menu {\\n    Menu() {}\\n}|false",
            "public class Menu {\\n    private Menu() {}\\n}|false",
            "public abstract class Menu {}|false",
            "public class Menu {\\n    static Menu create() {\\n        return new Menu(1);\\n    }\\n}|true",
    })
    void testHasPublicNoArgConstructor(final String source, final boolean expected) throws IOException {
        final Path file = tempDir.resolve("Menu.java");
        Files.writeString(file, source.replace("\\n", "\n"));
        assertThat(StoryFileCreator.hasPublicNoArgConstructor(file, "Menu")).isEqualTo(expected);
    }

    @Test
    void testHasPublicNoArgConstructorUnreadable() {
        assertThat(StoryFileCreator.hasPublicNoArgConstructor(tempDir.resolve("Missing.java"), "Missing")).isTrue();
    }

    @Test
    void testAddImportInSortedPosition() {
        final String source = """
                package stroom.gwt.workbench.client;

                import stroom.gwt.workbench.client.widgets.ButtonStories;
                import stroom.gwt.workbench.client.widgets.TickBoxStories;
                import stroom.gwt.workbench.framework.client.story.StoryRegistry;

                public final class AllStories {}
                """;
        assertThat(StoryFileCreator.addImport(source, "stroom.gwt.workbench.client.widgets.MenuStories"))
                .contains("""
                        import stroom.gwt.workbench.client.widgets.ButtonStories;
                        import stroom.gwt.workbench.client.widgets.MenuStories;
                        import stroom.gwt.workbench.client.widgets.TickBoxStories;
                        import stroom.gwt.workbench.framework.client.story.StoryRegistry;

                        public""");
        assertThat(StoryFileCreator.addImport(source, "stroom.gwt.workbench.client.widgets.ZebraStories"))
                .contains("""
                        import stroom.gwt.workbench.client.widgets.TickBoxStories;
                        import stroom.gwt.workbench.client.widgets.ZebraStories;
                        import stroom.gwt.workbench.framework.client.story.StoryRegistry;
                        """);
        assertThat(StoryFileCreator.addImport(source, "stroom.gwt.workbench.client.AStories"))
                .contains("""
                        package stroom.gwt.workbench.client;

                        import stroom.gwt.workbench.client.AStories;
                        import stroom.gwt.workbench.client.widgets.ButtonStories;
                        """);
    }

    @Test
    void testAddImportInNewGroup() {
        final String source = """
                package app;

                import stroom.a.B;

                import java.util.List;

                import static org.Foo.bar;

                class X {}
                """;
        assertThat(StoryFileCreator.addImport(source, "com.c.D")).isEqualTo("""
                package app;

                import stroom.a.B;

                import com.c.D;

                import java.util.List;

                import static org.Foo.bar;

                class X {}
                """);
        assertThat(StoryFileCreator.addImport("package app;\n\nimport stroom.a.B;\n\nclass X {}\n", "java.util.Map"))
                .isEqualTo("package app;\n\nimport stroom.a.B;\n\nimport java.util.Map;\n\nclass X {}\n");
        assertThat(StoryFileCreator.addImport("package app;\n\nclass X {}\n", "stroom.a.B"))
                .isEqualTo("package app;\n\nimport stroom.a.B;\n\nclass X {}\n");
    }

    @Test
    void testCompareImports() {
        assertThat(StoryFileCreator.compareImports("a.b.C", "a.b.D")).isNegative();
        // Compared part by part, so a shorter package part comes first
        assertThat(StoryFileCreator.compareImports("a.b.C", "a.bc.A")).isNegative();
        assertThat(StoryFileCreator.compareImports("stroom.gwt.workbench.X", "stroom.gwt.workbench.framework.A"))
                .isNegative();
        // Upper case comes before lower case
        assertThat(StoryFileCreator.compareImports("a.Z", "a.b")).isNegative();
        assertThat(StoryFileCreator.compareImports("a.b", "a.b")).isZero();
    }
}
