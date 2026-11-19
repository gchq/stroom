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

package stroom.ui.config.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// In this module, rather than stroom-core-shared, as this module holds Ace's theme files
class TestAceEditorTheme {

    private static final Path ACE_DIR = Path.of("src/main/java/edu/ycp/cs/dh/acegwt/public/ace");
    private static final Path STROOM_THEMES_CSS = Path.of(
            "../stroom-app/src/main/resources/ui/css/editor-themes.css");
    private static final Pattern THEME_FILE = Pattern.compile("theme-(\\w+)\\.js");
    private static final Pattern IS_DARK = Pattern.compile("isDark\\s*=\\s*(true|false)");

    @Test
    void testEveryAceThemeCanBeChosen() throws IOException {
        // The user preferences offer the enum's themes, so it must have every theme Ace has
        final Map<String, ThemeType> aceThemes = aceThemes();
        final Map<String, ThemeType> enumThemes = Arrays.stream(AceEditorTheme.values())
                .filter(theme -> !theme.isStroomTheme())
                .collect(Collectors.toMap(AceEditorTheme::getName, AceEditorTheme::getThemeType));

        assertThat(aceThemes).isNotEmpty();
        assertThat(enumThemes).isEqualTo(aceThemes);
    }

    @Test
    void testDefaultThemesAreOfTheirType() {
        assertThat(AceEditorTheme.DEFAULT_LIGHT_THEME.getThemeType()).isEqualTo(ThemeType.LIGHT);
        assertThat(AceEditorTheme.DEFAULT_DARK_THEME.getThemeType()).isEqualTo(ThemeType.DARK);
    }

    @Test
    void testDefaultThemesAreStroomThemes() {
        assertThat(AceEditorTheme.DEFAULT_LIGHT_THEME).isEqualTo(AceEditorTheme.STROOM_LIGHT);
        assertThat(AceEditorTheme.DEFAULT_DARK_THEME).isEqualTo(AceEditorTheme.STROOM_DARK);
        assertThat(AceEditorTheme.getDefaultEditorTheme(ThemeType.LIGHT)).isEqualTo(AceEditorTheme.STROOM_LIGHT);
        assertThat(AceEditorTheme.getDefaultEditorTheme(ThemeType.DARK)).isEqualTo(AceEditorTheme.STROOM_DARK);
    }

    @Test
    void testStroomThemesAreListedFirst() {
        assertThat(AceEditorTheme.getThemesByType(ThemeType.LIGHT))
                .startsWith(AceEditorTheme.STROOM_LIGHT)
                .containsOnlyOnce(AceEditorTheme.STROOM_LIGHT)
                .doesNotContain(AceEditorTheme.STROOM_DARK);
        assertThat(AceEditorTheme.getThemesByType(ThemeType.DARK))
                .startsWith(AceEditorTheme.STROOM_DARK)
                .containsOnlyOnce(AceEditorTheme.STROOM_DARK)
                .doesNotContain(AceEditorTheme.STROOM_LIGHT);
        // The rest are in name order, as before
        final List<AceEditorTheme> others = AceEditorTheme.getThemesByType(ThemeType.DARK)
                .subList(1, AceEditorTheme.getThemesByType(ThemeType.DARK).size());
        assertThat(others)
                .isSortedAccordingTo(Comparator.comparing(AceEditorTheme::getName))
                .noneMatch(AceEditorTheme::isStroomTheme);
    }

    @Test
    void testStroomThemesAreStyledAndHaveNoAceFile() throws IOException {
        // Ace finds a theme by its file, so a Stroom theme with one would be Ace's instead
        final String css = Files.readString(STROOM_THEMES_CSS);
        final List<AceEditorTheme> stroomThemes = Arrays.stream(AceEditorTheme.values())
                .filter(AceEditorTheme::isStroomTheme)
                .toList();

        assertThat(stroomThemes).containsExactly(AceEditorTheme.STROOM_LIGHT, AceEditorTheme.STROOM_DARK);
        for (final AceEditorTheme theme : stroomThemes) {
            assertThat(ACE_DIR.resolve("theme-" + theme.getName() + ".js")).doesNotExist();
            // StroomAceThemes gives the editor this class
            assertThat(css).contains(".ace-" + theme.getName().replace('_', '-') + " ");
        }
    }

    // Each theme-*.js file's theme name, and whether Ace says it is dark
    private static Map<String, ThemeType> aceThemes() throws IOException {
        try (final Stream<Path> files = Files.list(ACE_DIR)) {
            return files
                    .filter(file -> THEME_FILE.matcher(file.getFileName().toString()).matches())
                    .collect(Collectors.toMap(TestAceEditorTheme::themeName, TestAceEditorTheme::themeType));
        }
    }

    private static String themeName(final Path file) {
        final Matcher matcher = THEME_FILE.matcher(file.getFileName().toString());
        assertThat(matcher.matches()).isTrue();
        return matcher.group(1);
    }

    private static ThemeType themeType(final Path file) {
        try {
            final Matcher matcher = IS_DARK.matcher(Files.readString(file));
            assertThat(matcher.find())
                    .as("isDark in %s", file)
                    .isTrue();
            return Boolean.parseBoolean(matcher.group(1))
                    ? ThemeType.DARK
                    : ThemeType.LIGHT;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
