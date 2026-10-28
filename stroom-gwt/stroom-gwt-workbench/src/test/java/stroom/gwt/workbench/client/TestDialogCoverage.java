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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.client.DialogCoverage.Coverage;
import stroom.gwt.workbench.client.DialogCoverage.Declaration;
import stroom.gwt.workbench.client.DialogCoverage.SourceFile;
import stroom.gwt.workbench.framework.client.story.Story;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestDialogCoverage {

    @Test
    void testParseDeclaration() {
        final Declaration declaration = DialogCoverage.parseDeclaration("FooController", """
                package a.client;

                import b.Bar;

                public class FooController extends TabbedController<FooController.Foo>
                        implements HasHandlers, Focus<Map<String, String>> {
                }""");

        assertThat(declaration)
                .isEqualTo(new Declaration(true, false, "TabbedController", List.of("HasHandlers", "Focus")));
    }

    @Test
    void testParseDeclaration_abstractAndQualified() {
        final Declaration declaration = DialogCoverage.parseDeclaration("BaseEditor", """
                public abstract class BaseEditor<D> extends stroom.x.DocumentEditor<Base, D> {
                }""");

        assertThat(declaration)
                .isEqualTo(new Declaration(true, true, "DocumentEditor", List.of()));
    }

    @Test
    void testParseDeclaration_interfaceAndMissing() {
        assertThat(DialogCoverage.parseDeclaration("Thing", "public interface Thing extends TabData {\n}"))
                .isEqualTo(new Declaration(false, false, "TabData", List.of()));
        assertThat(DialogCoverage.parseDeclaration("Other", "public class Thing {\n}"))
                .isNull();
    }

    @Test
    void testReferencedTypeNames() {
        final String source = """
                import a.Imported;

                class Foo {
                    private final BarController bar = null;
                    void run() { ControllerDialog.show(this); int x = 1; }
                }""";

        assertThat(DialogCoverage.referencedTypeNames(source, false))
                .contains("Foo", "BarController", "ControllerDialog")
                .doesNotContain("Imported", "x");
        assertThat(DialogCoverage.referencedTypeNames(source, true))
                .contains("Imported");
    }

    @Test
    void testResolveDialogType() {
        final String source = """
                class OwnerController {
                    private final Provider<EditController> editProvider;
                    private final LazyValue<InfoController> info;
                    void show() {
                        final ChooserController chooser = chooserProvider.get();
                        ControllerDialog.showOnce(this, chooser).fire();
                    }
                }""";

        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "this"))
                .isEqualTo("OwnerController");
        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "OwnerController.this"))
                .isEqualTo("OwnerController");
        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "editProvider.get()"))
                .isEqualTo("EditController");
        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "info"))
                .isEqualTo("InfoController");
        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "chooser"))
                .isEqualTo("ChooserController");
        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "unknown"))
                .isNull();
        assertThat(DialogCoverage.resolveDialogType("OwnerController", source, "getChooser(1)"))
                .isNull();
    }

    @Test
    void testCoverage() {
        final Map<String, SourceFile> sources = new LinkedHashMap<>();
        // The controller base, screen bases, and screens
        add(sources, "Controller", "/component/", "public abstract class Controller {\n}");
        add(sources, "TabData", "/tab/", "public interface TabData {\n}");
        add(sources, "DocumentEditor", "/doc/",
                "public abstract class DocumentEditor<D> extends Controller implements TabData {\n}");
        add(sources, "BaseEditor", "/base/", "public abstract class BaseEditor extends DocumentEditor<D> {\n}");
        add(sources, "FeedEditor", "/feed/", """
                public class FeedEditor extends BaseEditor {
                    private final Provider<FeedSettingsController> settingsProvider;
                    void edit() { ControllerDialog.showOnce(this, settingsProvider.get()).fire(); }
                }""");
        add(sources, "UsersController", "/users/",
                "public class UsersController extends Controller implements TabData {\n}");
        // Describes a tab, but isn't a screen
        add(sources, "TabDataImpl", "/tab/", "public class TabDataImpl implements TabData {\n}");
        add(sources, "NodesEditor", "/nodes/", "public class NodesEditor extends DocumentEditor<D> {\n}");
        add(sources, "PathwayEditor", "/pathways/", "public class PathwayEditor extends DocumentEditor<D> {\n}");
        // Dialogs
        add(sources, "FeedSettingsController", "/feed/",
                "public class FeedSettingsController extends Controller {\n}");
        add(sources, "AboutController", "/about/", """
                public class AboutController extends Controller {
                    void show() { ControllerDialog.show(this).fire(); }
                }""");
        add(sources, "TooltipController", "/tooltip/", """
                public class TooltipController extends Controller {
                    void show() { ShowPopupEvent.builder(this).fire(); }
                }""");
        add(sources, "OddController", "/odd/", """
                public class OddController extends Controller {
                    void show() { ControllerDialog.showOnce(appRoot, make(1)).fire(); }
                }""");

        final DialogCoverage coverage = new DialogCoverage(
                sources,
                Set.of("FeedEditor", "UsersController"),
                Map.of("AboutController", "some-story--about"));

        // Abstract classes and the excluded pathways package aren't screens
        assertThat(coverage.getScreens())
                .containsExactly("FeedEditor", "NodesEditor", "UsersController");
        assertThat(coverage.getDialogs())
                .containsExactly("AboutController", "FeedSettingsController", "TooltipController");
        assertThat(coverage.getUnresolvedDialogs())
                .containsExactly("OddController: make(1)");

        assertThat(coverage.getCoverage("FeedEditor"))
                .isEqualTo(Coverage.STORY);
        // Only named by a covered controller
        assertThat(coverage.getCoverage("FeedSettingsController"))
                .isEqualTo(Coverage.REACHED);
        assertThat(coverage.getCoverage("NodesEditor"))
                .isEqualTo(Coverage.NONE);
        // A play opens it, by the evidence
        assertThat(coverage.getCoverage("AboutController"))
                .isEqualTo(Coverage.STORY);

        assertThat(coverage.report())
                .contains("Screens: 3, covered by a story: 2, only reached through another controller: 0, "
                          + "not covered: 1")
                .contains("Dialogs: 3, covered by a story: 1, only reached through another controller: 1, "
                          + "not covered: 1")
                .contains("    NodesEditor\n")
                .contains("  OddController: make(1)\n");
    }

    /// Checks the evidence file: every story it names exists, and every class it names is a screen
    /// or dialog that no story names (else it isn't needed).
    @Test
    void testEvidence() {
        final Set<String> storyIds = AllStories.create()
                .getStories()
                .stream()
                .map(Story::getId)
                .collect(Collectors.toSet());
        final DialogCoverage coverage = DialogCoverage.load(Path.of("../..").toAbsolutePath().normalize());

        coverage.getEvidence().forEach((name, storyId) -> {
            assertThat(storyIds)
                    .as("The story for " + name)
                    .contains(storyId);
            assertThat(coverage.getScreens().contains(name) || coverage.getDialogs().contains(name))
                    .as(name + " is a screen or dialog")
                    .isTrue();
        });
    }

    private static void add(final Map<String, SourceFile> sources,
                            final String name,
                            final String packagePath,
                            final String source) {
        sources.put(name, new SourceFile(name, "src/main/java/stroom" + packagePath + "client/" + name + ".java",
                source));
    }
}
