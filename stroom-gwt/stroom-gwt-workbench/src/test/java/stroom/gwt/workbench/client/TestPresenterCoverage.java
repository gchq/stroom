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

import stroom.gwt.workbench.client.PresenterCoverage.Coverage;
import stroom.gwt.workbench.client.PresenterCoverage.Declaration;
import stroom.gwt.workbench.client.PresenterCoverage.SourceFile;
import stroom.gwt.workbench.framework.client.story.Story;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestPresenterCoverage {

    @Test
    void testParseDeclaration() {
        final Declaration declaration = PresenterCoverage.parseDeclaration("FooPresenter", """
                package a.client;

                import b.Bar;

                public class FooPresenter extends MyPresenterWidget<FooPresenter.FooView>
                        implements HasHandlers, Focus<Map<String, String>> {
                }""");

        assertThat(declaration)
                .isEqualTo(new Declaration(true, false, "MyPresenterWidget", List.of("HasHandlers", "Focus")));
    }

    @Test
    void testParseDeclaration_abstractAndQualified() {
        final Declaration declaration = PresenterCoverage.parseDeclaration("BasePresenter", """
                public abstract class BasePresenter<D> extends stroom.x.DocTabPresenter<View, D> {
                }""");

        assertThat(declaration)
                .isEqualTo(new Declaration(true, true, "DocTabPresenter", List.of()));
    }

    @Test
    void testParseDeclaration_interfaceAndMissing() {
        assertThat(PresenterCoverage.parseDeclaration("Thing", "public interface Thing extends TabData {\n}"))
                .isEqualTo(new Declaration(false, false, "TabData", List.of()));
        assertThat(PresenterCoverage.parseDeclaration("Other", "public class Thing {\n}"))
                .isNull();
    }

    @Test
    void testReferencedTypeNames() {
        final String source = """
                import a.Imported;

                class Foo {
                    private final BarPresenter bar = null;
                    void run() { ShowPopupEvent.builder(this); int x = 1; }
                }""";

        assertThat(PresenterCoverage.referencedTypeNames(source, false))
                .contains("Foo", "BarPresenter", "ShowPopupEvent")
                .doesNotContain("Imported", "x");
        assertThat(PresenterCoverage.referencedTypeNames(source, true))
                .contains("Imported");
    }

    @Test
    void testResolveDialogType() {
        final String source = """
                class OwnerPresenter {
                    private final Provider<EditPresenter> editPresenterProvider;
                    private final LazyValue<InfoPresenter> infoPresenter;
                    void show() {
                        final ChooserPresenter chooser = chooserProvider.get();
                        ShowPopupEvent.builder(chooser).fire();
                    }
                }""";

        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "this"))
                .isEqualTo("OwnerPresenter");
        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "OwnerPresenter.this"))
                .isEqualTo("OwnerPresenter");
        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "editPresenterProvider.get()"))
                .isEqualTo("EditPresenter");
        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "infoPresenter"))
                .isEqualTo("InfoPresenter");
        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "chooser"))
                .isEqualTo("ChooserPresenter");
        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "unknown"))
                .isNull();
        assertThat(PresenterCoverage.resolveDialogType("OwnerPresenter", source, "getPresenter(1)"))
                .isNull();
    }

    @Test
    void testCoverage() {
        final Map<String, SourceFile> sources = new LinkedHashMap<>();
        // Screen bases, and screens
        add(sources, "DocTabPresenter", "/doc/", "public abstract class DocTabPresenter<V> {\n}");
        add(sources, "TabData", "/tab/", "public interface TabData {\n}");
        add(sources, "BaseScreen", "/base/", "public abstract class BaseScreen extends DocTabPresenter<V> {\n}");
        add(sources, "FeedPresenter", "/feed/", """
                public class FeedPresenter extends BaseScreen {
                    private final Provider<FeedSettingsPresenter> settingsProvider;
                    void edit() { ShowPopupEvent.builder(settingsProvider.get()).fire(); }
                }""");
        add(sources, "UsersPresenter", "/users/", "public class UsersPresenter implements TabData {\n}");
        // Describes a tab, but isn't a screen
        add(sources, "TabDataImpl", "/tab/", "public class TabDataImpl implements TabData {\n}");
        add(sources, "NodesPresenter", "/nodes/", "public class NodesPresenter extends DocTabPresenter<V> {\n}");
        add(sources, "PathwayPresenter", "/pathways/", "public class PathwayPresenter extends DocTabPresenter<V> {\n}");
        // Dialogs
        add(sources, "FeedSettingsPresenter", "/feed/", "public class FeedSettingsPresenter {\n}");
        add(sources, "AboutPresenter", "/about/", """
                public class AboutPresenter {
                    void show() { ShowPopupEvent.builder(this).fire(); }
                }""");
        add(sources, "OddPresenter", "/odd/", """
                public class OddPresenter {
                    void show() { ShowPopupEvent.builder(make(1)).fire(); }
                }""");

        final PresenterCoverage coverage = new PresenterCoverage(
                sources,
                Set.of("FeedPresenter", "UsersPresenter"),
                Map.of("AboutPresenter", "some-story--about"));

        // Abstract classes and the excluded pathways package aren't screens
        assertThat(coverage.getScreens())
                .containsExactly("FeedPresenter", "NodesPresenter", "UsersPresenter");
        assertThat(coverage.getDialogs())
                .containsExactly("AboutPresenter", "FeedSettingsPresenter");
        assertThat(coverage.getUnresolvedDialogs())
                .containsExactly("OddPresenter: make(1)");

        assertThat(coverage.getCoverage("FeedPresenter"))
                .isEqualTo(Coverage.STORY);
        // Only named by a covered presenter
        assertThat(coverage.getCoverage("FeedSettingsPresenter"))
                .isEqualTo(Coverage.REACHED);
        assertThat(coverage.getCoverage("NodesPresenter"))
                .isEqualTo(Coverage.NONE);
        // A play opens it, by the evidence
        assertThat(coverage.getCoverage("AboutPresenter"))
                .isEqualTo(Coverage.STORY);

        assertThat(coverage.report())
                .contains("Screens: 3, covered by a story: 2, only reached through another presenter: 0, "
                          + "not covered: 1")
                .contains("Dialogs: 2, covered by a story: 1, only reached through another presenter: 1, "
                          + "not covered: 0")
                .contains("    NodesPresenter\n")
                .contains("  OddPresenter: make(1)\n");
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
        final PresenterCoverage coverage = PresenterCoverage.load(Path.of("../..").toAbsolutePath().normalize());

        assertThat(coverage.getEvidence()).isNotEmpty();
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
