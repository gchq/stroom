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

import stroom.ui.config.shared.UserPreferences.ToolbarDisplay;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TestThemeCssUtil {

    @Test
    void iconsOnlyNeedsNoClass() {
        assertThat(ThemeCssUtil.getToolbarDisplayClass(ToolbarDisplay.ICONS)).isNull();
        assertThat(ThemeCssUtil.getToolbarDisplayClass(null)).isNull();
    }

    @Test
    void eachOtherDisplayHasItsClass() {
        assertThat(ThemeCssUtil.getToolbarDisplayClass(ToolbarDisplay.ICONS_AND_TEXT))
                .isEqualTo("toolbar-display--icons-and-text");
        assertThat(ThemeCssUtil.getToolbarDisplayClass(ToolbarDisplay.TEXT))
                .isEqualTo("toolbar-display--text");
    }

    @Test
    void thePageClassesSayHowToolbarsShow() {
        final UserPreferences text = UserPreferences.builder().toolbarDisplay(ToolbarDisplay.TEXT).build();
        assertThat(ThemeCssUtil.getCurrentPreferenceClasses(text).split(" "))
                .contains("toolbar-display--text");

        final UserPreferences icons = UserPreferences.builder().build();
        assertThat(ThemeCssUtil.getCurrentPreferenceClasses(icons)).doesNotContain("toolbar-display");
    }

    @Test
    void toolbarsShowIconsUnlessChosen() {
        assertThat(UserPreferences.builder().build().getToolbarDisplay()).isEqualTo(ToolbarDisplay.ICONS);
        assertThat(UserPreferences.builder().toolbarDisplay(null).build().getToolbarDisplay())
                .isEqualTo(ToolbarDisplay.ICONS);
    }

    @Test
    void theDisplayIsSavedAndReadBack() throws Exception {
        final JsonMapper mapper = JsonUtil.getMapper();
        final UserPreferences saved = UserPreferences.builder()
                .toolbarDisplay(ToolbarDisplay.ICONS_AND_TEXT)
                .build();
        final UserPreferences read = mapper.readValue(mapper.writeValueAsString(saved), UserPreferences.class);
        assertThat(read.getToolbarDisplay()).isEqualTo(ToolbarDisplay.ICONS_AND_TEXT);
    }

    @Test
    void preferencesSavedBeforeTheDisplayShowIcons() throws Exception {
        final UserPreferences read = JsonUtil.getMapper().readValue("{\"theme\":\"Dark\"}", UserPreferences.class);
        assertThat(read.getToolbarDisplay()).isEqualTo(ToolbarDisplay.ICONS);
    }

    @Test
    void aDisplayIsFoundByItsName() {
        assertThat(ToolbarDisplay.fromDisplayValue("Icons and Text")).isEqualTo(ToolbarDisplay.ICONS_AND_TEXT);
        assertThat(ToolbarDisplay.fromDisplayValue("text")).isEqualTo(ToolbarDisplay.TEXT);
        assertThat(ToolbarDisplay.fromDisplayValue("Unknown")).isEqualTo(ToolbarDisplay.DEFAULT);
        assertThat(ToolbarDisplay.fromDisplayValue(null)).isEqualTo(ToolbarDisplay.DEFAULT);
    }
}
