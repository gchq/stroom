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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoryUrls {

    @Test
    void testUrls() {
        final String id = "widgets-buttons-button--default";
        assertThat(StoryUrls.managerPath(id)).isEqualTo("/story/widgets-buttons-button--default");
        assertThat(StoryUrls.managerUrl(id)).isEqualTo("?path=/story/widgets-buttons-button--default");
        assertThat(StoryUrls.previewUrl(id))
                .isEqualTo("iframe.html?id=widgets-buttons-button--default&viewMode=story");
    }

    @Test
    void testUrlsWithArgs() {
        final String id = "a--b";
        assertThat(StoryUrls.managerUrl(id, "loading:!true;text:Hi there"))
                .isEqualTo("?path=/story/a--b&args=loading:!true;text:Hi+there");
        // The args' own escapes survive the URL being decoded
        assertThat(StoryUrls.previewUrl(id, "text:100%25"))
                .isEqualTo("iframe.html?id=a--b&viewMode=story&args=text:100%2525");
        assertThat(StoryUrls.managerUrl(id, "")).isEqualTo("?path=/story/a--b");
        assertThat(StoryUrls.previewUrl(id, null)).isEqualTo("iframe.html?id=a--b&viewMode=story");
    }

    @Test
    void testStoryIdFromPath() {
        assertThat(StoryUrls.storyIdFromPath("/story/widgets-buttons-button--default"))
                .isEqualTo("widgets-buttons-button--default");
        assertThat(StoryUrls.storyIdFromPath(StoryUrls.managerPath("a--b")))
                .isEqualTo("a--b");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/story/", "/story/  ", "/docs/widgets-buttons-button--docs", "story/a--b"})
    void testStoryIdFromPath_notAStory(final String path) {
        assertThat(StoryUrls.storyIdFromPath(path)).isNull();
    }

    @Test
    void testSettingsUrls() {
        assertThat(StoryUrls.settingsUrl("about")).isEqualTo("?path=/settings/about");
        assertThat(StoryUrls.settingsPageFromPath("/settings/shortcuts")).isEqualTo("shortcuts");
        assertThat(StoryUrls.settingsPageFromPath("/settings/")).isNull();
        assertThat(StoryUrls.settingsPageFromPath("/story/a--b")).isNull();
        assertThat(StoryUrls.settingsPageFromPath(null)).isNull();
        assertThat(StoryUrls.storyIdFromPath("/settings/about")).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/iframe.html,true",
            "/workbench/iframe.html,true",
            "/,false",
            "/index.html,false",
            "/myiframe.html,false",
    })
    void testIsPreviewPage(final String pagePath, final boolean expected) {
        assertThat(StoryUrls.isPreviewPage(pagePath)).isEqualTo(expected);
    }

    @Test
    void testIsPreviewPage_null() {
        assertThat(StoryUrls.isPreviewPage(null)).isFalse();
    }
}
