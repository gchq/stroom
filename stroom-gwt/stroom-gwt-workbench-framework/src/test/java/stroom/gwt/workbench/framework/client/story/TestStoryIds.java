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

package stroom.gwt.workbench.framework.client.story;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoryIds {

    // Expected values taken from the ids/names the React Storybook generates
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Widgets/Buttons/Button|widgets-buttons-button",
            "Widgets/Editors & Viewers/AceEditor|widgets-editors-viewers-aceeditor",
            "With Icons|with-icons",
            "Dialog — Close button|dialog-close-button",
            "  --Leading and trailing--  |leading-and-trailing",
            "Alerts (info / warn / error)|alerts-info-warn-error",
            "S 3 Config|s-3-config",
            "already-sanitised|already-sanitised",
            "Café Crème|café-crème",
            "Ünïcödé/Ñame|ünïcödé-ñame",
            "$|''",
            "''|''",
    })
    void testSanitise(final String input, final String expected) {
        assertThat(StoryIds.sanitise(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Default|Default",
            "WithIcons|With Icons",
            "S3Config|S 3 Config",
            "XMLSchema|XML Schema",
            "ExplorerTypeFilter|Explorer Type Filter",
            "Tick_box|Tick Box",
            "lowerCamel|Lower Camel",
            "Version2Beta|Version 2 Beta",
            "with.dots-and_underscores|With Dots And Underscores",
            "already Spaced|Already Spaced",
            "ABC|ABC",
            "A1B2|A 1 B 2",
            // Non-ASCII letters and other symbols are kept, as in Storybook (they were dropped in
            // the browser, where Character.isLetter only knows ASCII)
            "Café|Café",
            "naïveTest|Naïve Test",
            "ÉtéFun|Été Fun",
            "Price$Value|Price$ Value",
            "$|$",
            "über|über",
            "''|''",
    })
    void testStoryNameFromExport(final String exportName, final String expected) {
        assertThat(StoryIds.storyNameFromExport(exportName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Widgets/Buttons/Button|With Icons|widgets-buttons-button--with-icons",
            "Widgets/Inputs/TickBox|No Label|widgets-inputs-tickbox--no-label",
            "App/Main/AppShell|Default|app-main-appshell--default",
    })
    void testStoryId(final String title, final String name, final String expected) {
        assertThat(StoryIds.storyId(title, name)).isEqualTo(expected);
    }

    @Test
    void testStoryId_noName() {
        // As in Storybook, there's no '--' without a name
        assertThat(StoryIds.storyId("Widgets/Button", "")).isEqualTo("widgets-button");
        assertThat(StoryIds.storyId("Widgets/Button", null)).isEqualTo("widgets-button");
    }

    @Test
    void testStoryId_nonAscii() {
        assertThat(StoryIds.storyId("Widgets/Café", "Crème Brûlée")).isEqualTo("widgets-café--crème-brûlée");
    }

    @Test
    void testStoryId_nothingLeftAfterSanitising() {
        // Storybook throws for these too
        assertThatThrownBy(() -> StoryIds.storyId("$", "Default"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'$'");
        assertThatThrownBy(() -> StoryIds.storyId("Widgets/Button", "$"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> StoryIds.storyId(null, "Default"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNull() {
        assertThat(StoryIds.sanitise(null)).isEmpty();
        assertThat(StoryIds.storyNameFromExport(null)).isEmpty();
    }
}
