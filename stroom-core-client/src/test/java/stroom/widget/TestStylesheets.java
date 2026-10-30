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

package stroom.widget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks the UI's stylesheets against the code that sets their classes (UI-GUIDE.md, "Its own
/// stylesheet, BEM"): every sheet is loaded and well formed, every class is named BEM style, every
/// class the code sets is styled (or is a block whose elements are, or a listed hook), and every
/// block a sheet styles is named somewhere in the code. A class set but never styled, or a block
/// styled but never set, is found here; an element's or modifier's rule that nothing sets any more
/// is found by the workbench's class census (`--class-census`, test-runner/README.md).
class TestStylesheets {

    private static final String CSS_DIR = "stroom-app/src/main/resources/ui/css";

    // The sheets pages load (index.html's app.css, a visualisation frame's vis.css and vis-legacy.css)
    private static final List<String> ROOT_SHEETS = List.of("app.css", "vis.css", "vis-legacy.css");

    // Sheets whose classes are other code's: Ace's editor themes, Prism's syntax highlighting, and
    // the legacy visualisation library's (set by its scripts, in a visualisation's frame)
    private static final List<String> THIRD_PARTY_SHEETS = List.of("editor-themes.css", "prismjs/", "vis-legacy.css");

    // Where classes are set: the client code, the shared code it compiles (e.g. the conditional
    // formatting styles' and the font preferences' classes), and the pages
    private static final List<String> CODE_DIRS = List.of(
            "stroom-core-client/src/main",
            "stroom-core-client-widget/src/main/java/stroom",
            "stroom-dashboard-gwt/src/main",
            "stroom-app-gwt/src/main",
            "stroom-statistics/stroom-statistics-client/src/main",
            "stroom-core-shared/src/main/java",
            "stroom-query/stroom-query-api/src/main/java",
            "stroom-core/src/main/resources/stroom/core/servlet");
    private static final String PAGES_DIR = "stroom-app/src/main/resources/ui";

    // Names that aren't Stroom's to choose: Ace's, the annotation types Stroom gives Ace's gutter
    // (which Ace makes classes of), and the box GWT's PopupPanel puts a popup's content in
    private static final Pattern NOT_STROOMS = Pattern.compile(
            "ace_[\\w-]+|stroom_(info|warning|error|fatal_error)|popupContent");

    // Classes the code sets that nothing styles, kept as hooks: for stories, scripts and code to find
    // an element by (ElementUtil, querySelector), or a part a layout helper styles
    private static final Set<String> HOOKS = Set.of(
            "code-editor__load-error-container",
            "colour-swatch-cell__text", "conditional-formatting-rule-editor__type",
            "create-document-types__icon",
            "date-time-box__input", "date-time-view__column",
            "delete-confirm__summary-header", "delete-confirm__type-count", "dialog--resizable", "dock-min",
            "draggable-tree__status", "embedded-query", "expression-tree-panel", "expression-tree-panel__canvas",
            "expression-tree-panel__items",
            "file-chooser__button", "form-group__help-text", "form-page", "line-col-input", "line-col-input__field",
            "navigation__name-filter-panel", "password-box__status", "pathway-tree__node-icon",
            "pipeline-tree__canvas", "pipeline-tree__items", "query-buttons__button", "query-edit__results",
            "schedule-box__input", "selection-box__control", "selection-list__filter", "sign-in-page__sign-in-button",
            "sign-in-page__user-name-outer", "source-viewer__character-navigator", "stepping-filter-editor__elements",
            "tab-layout__settings-button", "term-editor__item--field", "timeline-date-box", "value-spinner__status");

    // Sheets of shared patterns, each styling several blocks of its own (UI-GUIDE.md: the layout
    // helpers, the shared form patterns, the fonts and the conditional formatting's colours), and the
    // visualisation frame's sheet, whose classes (e.g. vis-point) are an interface the visualisation
    // packs, kept in their own repository and installed as content, style too
    private static final Set<String> SHARED_SHEETS = Set.of(
            "layout-helpers.css", "form-controls.css", "font.css", "conditional-formatting.css", "vis.css");

    // The page's settings, as classes on its root (the theme, density and font size), which any
    // sheet may style its block under, or set its custom properties for
    private static final Pattern ROOT_SETTINGS = Pattern.compile(
            "stroom-(theme|density|font-size)-[\\w-]+");

    // State words a state is given as a modifier, not as a class of its own (UI-GUIDE.md); only
    // `disabled`, which GWT's widgets set, is kept as a bare class
    private static final Set<String> STATE_WORDS = Set.of(
            "readonly", "selected", "empty", "active", "open", "checked", "expanded", "collapsed",
            "focused", "hover", "invalid", "error", "warning", "hidden", "maximised", "minimised",
            "visible", "enabled", "current", "dirty", "loading", "valid");

    // Generated, its SVGs' own classes (st0 …) styled by their own style elements
    private static final String GENERATED_SVG_IMAGES = "SvgImage.java";

    private static final Pattern BEM = Pattern.compile(
            "[a-z][a-z0-9]*(-[a-z0-9]+)*(__[a-z0-9]+(-[a-z0-9]+)*)?(--[a-z0-9]+(-[a-z0-9]+)*)?");
    private static final Pattern IMPORT = Pattern.compile("@import\\s+url\\(\"([^\"]+)\"\\)");
    private static final Pattern COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern SELECTOR = Pattern.compile("([^{};]+)\\{");
    private static final Pattern ATTRIBUTE_SELECTOR = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern CLASS_IN_SELECTOR = Pattern.compile("\\.(-?[_a-zA-Z][\\w-]*)");
    // A template's class attributes, less those set from its own ui:style ({style.name})
    private static final Pattern TEMPLATE_CLASSES = Pattern.compile(
            "\\b(?:class|styleName|addStyleNames|stylePrimaryName)=\"([^\"{]*)\"");
    // A class given by a literal to the methods that set or look for one, or in HTML built in a string
    private static final Pattern JAVA_CLASSES = Pattern.compile(
            "\\b(?:addStyleName|removeStyleName|setStyleName|setStylePrimaryName|addClassName|removeClassName"
            + "|setClassName|className|hasClassName|toggleClassName)\\(\\s*\"([^\"]*)\""
            + "|class=\\\\\"([^\"\\\\]*)\\\\\"");
    // Strings (in Java, and in JSNI's JavaScript, single quoted) and attribute values
    private static final Pattern STRING_LITERAL = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|=\"[^\"]*\"");
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]*");

    private static Path root;
    private static Map<String, String> sheets;
    private static Set<String> loaded;
    private static Map<String, Set<String>> defined;
    private static Map<String, Set<String>> codeClasses;
    private static Set<String> codeNames;

    @BeforeAll
    static void read() {
        root = repositoryRoot();
        sheets = new HashMap<>();
        try (final Stream<Path> paths = Files.walk(root.resolve(CSS_DIR))) {
            for (final Path path : paths.filter(p -> p.toString().endsWith(".css")).toList()) {
                sheets.put(root.resolve(CSS_DIR).relativize(path).toString().replace('\\', '/'), read(path));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        loaded = new LinkedHashSet<>();
        ROOT_SHEETS.forEach(TestStylesheets::load);

        defined = new HashMap<>();
        for (final String sheet : loaded) {
            if (THIRD_PARTY_SHEETS.stream().noneMatch(sheet::startsWith)) {
                for (final String name : classesIn(sheets.get(sheet))) {
                    defined.computeIfAbsent(name, k -> new TreeSet<>()).add(sheet);
                }
            }
        }

        codeClasses = new HashMap<>();
        codeNames = new HashSet<>();
        for (final String dir : CODE_DIRS) {
            for (final Path path : files(root.resolve(dir), ".java", ".ui.xml", ".html")) {
                final String text = read(path);
                final String where = root.relativize(path).toString();
                final Matcher classes = (path.toString().endsWith(".java") ? JAVA_CLASSES : TEMPLATE_CLASSES)
                        .matcher(path.endsWith(GENERATED_SVG_IMAGES) ? "" : text);
                while (classes.find()) {
                    final String value = classes.group(1) != null ? classes.group(1) : classes.group(2);
                    for (final String name : value.trim().split("\\s+")) {
                        // Not a page's placeholders (@ROOT_CLASS@) or a template's ({0}, ${...})
                        if (!name.isEmpty() && !name.matches(".*[{$@].*")) {
                            codeClasses.computeIfAbsent(name, k -> new TreeSet<>()).add(where);
                        }
                    }
                }
                namesIn(path.toString().endsWith(".html") ? text : strings(text));
            }
        }
        for (final Path path : files(root.resolve(PAGES_DIR), ".html", ".js")) {
            if (path.getParent().equals(root.resolve(PAGES_DIR))) {
                namesIn(read(path));
            }
        }
    }

    @Test
    void testEverySheetIsLoaded() {
        final List<String> problems = new ArrayList<>();
        for (final String sheet : loaded) {
            final Matcher imports = IMPORT.matcher(withoutComments(sheets.get(sheet)));
            while (imports.find()) {
                final String imported = imports.group(1);
                if (!imported.startsWith("../") && !sheets.containsKey(imported)) {
                    problems.add(sheet + " imports " + imported + ", which isn't there");
                }
            }
        }
        sheets.keySet().stream()
                .filter(sheet -> !loaded.contains(sheet))
                .sorted()
                .forEach(sheet -> problems.add(sheet + " isn't loaded by any page"));
        assertThat(problems).as("stylesheets not loaded").isEmpty();
    }

    @Test
    void testSheetsAreWellFormed() {
        final List<String> problems = new ArrayList<>();
        for (final Map.Entry<String, String> sheet : new TreeSet<>(sheets.keySet()).stream()
                .map(name -> Map.entry(name, sheets.get(name)))
                .toList()) {
            final String text = sheet.getValue();
            final int lastOpen = text.lastIndexOf("/*");
            if (lastOpen >= 0 && text.indexOf("*/", lastOpen + 2) < 0) {
                problems.add(sheet.getKey() + ": a comment isn't closed");
            }
            final String rules = QUOTED.matcher(withoutComments(text)).replaceAll("\"\"");
            if (count(rules, '{') != count(rules, '}')) {
                problems.add(sheet.getKey() + ": " + count(rules, '{') + " { but " + count(rules, '}') + " }");
            }
        }
        assertThat(problems).as("malformed stylesheets").isEmpty();
    }

    @Test
    void testStylesheetClassesAreBem() {
        final List<String> problems = new ArrayList<>();
        new TreeSet<>(defined.keySet()).stream()
                .filter(name -> !isBem(name))
                .forEach(name -> problems.add(name + " (" + String.join(", ", defined.get(name)) + ")"));
        assertThat(problems).as("stylesheet classes not named BEM style").isEmpty();
    }

    @Test
    void testCodeClassesAreBem() {
        final List<String> problems = new ArrayList<>();
        new TreeSet<>(codeClasses.keySet()).stream()
                .filter(name -> !isBem(name))
                .forEach(name -> problems.add(name + " (" + codeClasses.get(name).iterator().next() + ")"));
        assertThat(problems).as("classes set by the code not named BEM style").isEmpty();
    }

    @Test
    void testCodeClassesAreStyled() {
        final Set<String> styledBlocks = new HashSet<>();
        for (final String name : defined.keySet()) {
            styledBlocks.add(block(name));
        }
        final List<String> problems = new ArrayList<>();
        for (final String name : new TreeSet<>(codeClasses.keySet())) {
            if (!defined.containsKey(name)
                && !styledBlocks.contains(name)
                && !HOOKS.contains(name)
                && !NOT_STROOMS.matcher(name).matches()) {
                problems.add(name + " (" + codeClasses.get(name).iterator().next() + ")");
            }
        }
        assertThat(problems)
                .as("classes the code sets that no stylesheet styles: style them, drop them, or list them in HOOKS")
                .isEmpty();
        assertThat(HOOKS.stream().filter(defined::containsKey).sorted().toList())
                .as("HOOKS that are styled now, so aren't hooks")
                .isEmpty();
    }

    @Test
    void testEveryBlockIsSet() {
        final Map<String, Set<String>> blocks = new HashMap<>();
        defined.forEach((name, where) -> {
            if (isBem(name)) {
                blocks.computeIfAbsent(block(name), k -> new TreeSet<>()).addAll(where);
            }
        });
        final List<String> problems = new ArrayList<>();
        // A block is set if a name in the code is the block, or one of its elements or modifiers
        // (a template writes data-viewer__body; Java may build names from the block's own)
        final Set<String> setBlocks = new HashSet<>();
        codeNames.forEach(name -> setBlocks.add(block(name)));
        for (final String block : new TreeSet<>(blocks.keySet())) {
            if (!setBlocks.contains(block) && !NOT_STROOMS.matcher(block).matches()) {
                problems.add(block + " (" + String.join(", ", blocks.get(block)) + ")");
            }
        }
        assertThat(problems).as("blocks styled but named nowhere in the code").isEmpty();
    }

    @Test
    void testEachSheetStylesItsOwnBlock() {
        final List<String> problems = new ArrayList<>();
        for (final String sheet : new TreeSet<>(loaded)) {
            if (!isOwnSheet(sheet) || SHARED_SHEETS.contains(sheet)) {
                continue;
            }
            final Set<String> others = otherBlocksStyled(sheet);
            if (!others.isEmpty()) {
                problems.add(sheet + " styles " + String.join(", ", others));
            }
        }
        assertThat(problems)
                .as("sheets styling blocks other than the one they're named after (another block may be "
                    + "the context, e.g. `.data-grid .my-block__cell`, but not what is styled)")
                .isEmpty();
    }

    @Test
    void testNoBareStateClasses() {
        final Map<String, Set<String>> problems = new TreeMap<>();
        for (final String sheet : loaded) {
            if (isOwnSheet(sheet)) {
                for (final String name : classesIn(sheets.get(sheet))) {
                    if (STATE_WORDS.contains(name)) {
                        problems.computeIfAbsent(name, k -> new TreeSet<>()).add(sheet);
                    }
                }
            }
        }
        assertThat(problems)
                .as("bare state classes: give the block a modifier (`my-block--selected`) instead")
                .isEmpty();
    }

    @Test
    void testCodeIsFound() {
        // So that the checks above can't pass by finding nothing
        assertThat(loaded).hasSizeGreaterThan(200);
        assertThat(defined).hasSizeGreaterThan(1000);
        assertThat(codeClasses).hasSizeGreaterThan(500);
        assertThat(codeClasses).containsKeys("max", "dock-container-vertical", "control-box");
    }

    // The sheet and the sheets it imports, in the order they load
    private static void load(final String sheet) {
        if (!sheets.containsKey(sheet) || !loaded.add(sheet)) {
            return;
        }
        final Matcher imports = IMPORT.matcher(withoutComments(sheets.get(sheet)));
        while (imports.find()) {
            final String imported = imports.group(1);
            if (!imported.startsWith("../")) {
                load(imported);
            }
        }
    }

    // The classes a sheet's selectors name
    private static Set<String> classesIn(final String sheet) {
        final String rules = QUOTED.matcher(withoutComments(sheet)).replaceAll("\"\"");
        final Set<String> names = new HashSet<>();
        final Matcher selectors = SELECTOR.matcher(rules);
        while (selectors.find()) {
            final String selector = selectors.group(1).trim();
            if (!selector.startsWith("@")) {
                final Matcher classes = CLASS_IN_SELECTOR.matcher(ATTRIBUTE_SELECTOR.matcher(selector).replaceAll(""));
                while (classes.find()) {
                    names.add(classes.group(1));
                }
            }
        }
        return names;
    }

    // Stroom's own sheets at the top of the folder (not a third party's, nor one in a subfolder)
    private static boolean isOwnSheet(final String sheet) {
        return THIRD_PARTY_SHEETS.stream().noneMatch(sheet::startsWith) && !sheet.contains("/");
    }

    // The blocks other than the sheet's own (its name) that its rules style: in each selector, the
    // compound the rule applies to, unless it is also of the sheet's block (`.my-block.button`);
    // the compounds before it are context, and `disabled` is a state, not a block
    private static Set<String> otherBlocksStyled(final String sheet) {
        final String own = sheet.substring(0, sheet.length() - ".css".length());
        final String rules = QUOTED.matcher(withoutComments(sheets.get(sheet))).replaceAll("\"\"");
        final Set<String> others = new TreeSet<>();
        final Matcher selectors = SELECTOR.matcher(rules);
        while (selectors.find()) {
            final String group = selectors.group(1).trim();
            if (group.startsWith("@")) {
                continue;
            }
            for (final String selector : group.split(",")) {
                final String[] compounds = ATTRIBUTE_SELECTOR.matcher(selector.trim()).replaceAll("")
                        .split("\\s*[>+~]\\s*|\\s+");
                final List<String> blocks = new ArrayList<>();
                final Matcher classes = CLASS_IN_SELECTOR.matcher(compounds[compounds.length - 1]);
                while (classes.find()) {
                    final String name = classes.group(1);
                    if (!"disabled".equals(name)
                        && !NOT_STROOMS.matcher(name).matches()
                        && !ROOT_SETTINGS.matcher(name).matches()) {
                        blocks.add(block(name));
                    }
                }
                if (!blocks.contains(own)) {
                    others.addAll(blocks);
                }
            }
        }
        return others;
    }

    // The words in a file's strings and attribute values, which is where a class's name is written
    private static String strings(final String text) {
        final StringBuilder out = new StringBuilder();
        final Matcher strings = STRING_LITERAL.matcher(text);
        while (strings.find()) {
            out.append(strings.group()).append('\n');
        }
        return out.toString();
    }

    private static void namesIn(final String text) {
        final Matcher names = NAME.matcher(text);
        while (names.find()) {
            codeNames.add(names.group());
        }
    }

    private static boolean isBem(final String name) {
        return BEM.matcher(name).matches() || NOT_STROOMS.matcher(name).matches();
    }

    // A BEM name's block: up to its first __ or --
    private static String block(final String name) {
        final int element = name.indexOf("__");
        final int modifier = name.indexOf("--");
        int end = name.length();
        if (element > 0) {
            end = element;
        }
        if (modifier > 0 && modifier < end) {
            end = modifier;
        }
        return name.substring(0, end);
    }

    private static String withoutComments(final String text) {
        return COMMENT.matcher(text).replaceAll(" ");
    }

    private static int count(final String text, final char c) {
        return (int) text.chars().filter(ch -> ch == c).count();
    }

    private static List<Path> files(final Path dir, final String... endings) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (final Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(p -> Stream.of(endings).anyMatch(e -> p.toString().endsWith(e))).toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Tests run in their module's directory; the repository is its parent
    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("stroom-core-client-widget"))) {
            dir = dir.getParent();
        }
        assertThat(dir)
                .as("the repository's root, above " + Path.of("").toAbsolutePath())
                .isNotNull();
        return dir;
    }
}
