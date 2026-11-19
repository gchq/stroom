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

package stroom.gwt.workbench.client.widgets;

import stroom.gwt.workbench.framework.client.args.Args;
import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoryArgs {

    @Test
    void testToSvgImage() {
        assertThat(StoryArgs.toSvgImage("ADD")).isEqualTo(SvgImage.ADD);
        assertThat(StoryArgs.toSvgImage("EDIT")).isEqualTo(SvgImage.EDIT);
    }

    @Test
    void testToSvgImage_unknown() {
        // The icon arg can be set to anything in the URL, so an unknown one means no icon
        assertThat(StoryArgs.toSvgImage("NOT_AN_ICON")).isNull();
        assertThat(StoryArgs.toSvgImage("add")).isNull();
        assertThat(StoryArgs.toSvgImage("<script>")).isNull();
    }

    @Test
    void testToSvgImage_empty() {
        assertThat(StoryArgs.toSvgImage(null)).isNull();
        assertThat(StoryArgs.toSvgImage("")).isNull();
        assertThat(StoryArgs.toSvgImage("  ")).isNull();
    }

    @Test
    void testGetBoolean() {
        final Args args = Args.of("enabled", false, "visible", true, "text", "true");
        assertThat(StoryArgs.getBoolean(args, "enabled", true)).isFalse();
        assertThat(StoryArgs.getBoolean(args, "visible", false)).isTrue();
        // A string from the URL
        assertThat(StoryArgs.getBoolean(args, "text", false)).isTrue();
    }

    @Test
    void testGetBoolean_undefined() {
        final Args args = Args.of("enabled", null);
        assertThat(StoryArgs.getBoolean(args, "enabled", true)).isTrue();
        assertThat(StoryArgs.getBoolean(args, "missing", true)).isTrue();
        assertThat(StoryArgs.getBoolean(args, "missing", false)).isFalse();
    }

    @Test
    void testGetLong() {
        final Args args = Args.of("value", 42, "negative", -20, "fraction", 1.9, "text", "17");
        assertThat(StoryArgs.getLong(args, "value")).isEqualTo(42L);
        assertThat(StoryArgs.getLong(args, "negative")).isEqualTo(-20L);
        assertThat(StoryArgs.getLong(args, "fraction")).isEqualTo(1L);
        assertThat(StoryArgs.getLong(args, "text")).isEqualTo(17L);
    }

    @Test
    void testGetLong_invalid() {
        final Args args = Args.of("text", "abc", "infinite", Double.POSITIVE_INFINITY);
        assertThat(StoryArgs.getLong(args, "text")).isNull();
        assertThat(StoryArgs.getLong(args, "infinite")).isNull();
        assertThat(StoryArgs.getLong(args, "missing")).isNull();
    }
}
