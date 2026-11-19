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

package stroom.query.client.result.vis;

import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVisButtonSpec {

    @Test
    void testIconByName() {
        assertThat(VisButtonSpec.icon("DOWNLOAD")).isEqualTo(SvgImage.DOWNLOAD);
        // Ignoring case, and with - for _
        assertThat(VisButtonSpec.icon("download")).isEqualTo(SvgImage.DOWNLOAD);
        assertThat(VisButtonSpec.icon(" expand-all ")).isEqualTo(SvgImage.EXPAND_ALL);
    }

    @Test
    void testUnknownIcon() {
        assertThat(VisButtonSpec.icon("no-such-icon")).isEqualTo(VisButtonSpec.UNKNOWN_ICON);
        assertThat(VisButtonSpec.icon("")).isEqualTo(VisButtonSpec.UNKNOWN_ICON);
        assertThat(VisButtonSpec.icon(null)).isEqualTo(VisButtonSpec.UNKNOWN_ICON);
    }

    @Test
    void testButton() {
        final VisButtonSpec button = new VisButtonSpec("save", "Save as image", "download", null);
        assertThat(button.getId()).isEqualTo("save");
        assertThat(button.getTitle()).isEqualTo("Save as image");
        assertThat(button.getIcon()).isEqualTo(SvgImage.DOWNLOAD);
        assertThat(button.isToggle()).isFalse();
        assertThat(button.isPressed()).isFalse();
        assertThat(button.isEnabled()).isTrue();
    }

    @Test
    void testDisabled() {
        final VisButtonSpec button = new VisButtonSpec("zoneSave", "Save zones", "save", null, false);
        assertThat(button.isEnabled()).isFalse();
        assertThat(button.isToggle()).isFalse();
        assertThat(new VisButtonSpec("legend", "Show legend", "eye", true, false).isEnabled()).isFalse();
    }

    @Test
    void testToggle() {
        assertThat(new VisButtonSpec("legend", "Show legend", "eye", true).isPressed()).isTrue();
        final VisButtonSpec off = new VisButtonSpec("legend", "Show legend", "eye", false);
        assertThat(off.isToggle()).isTrue();
        assertThat(off.isPressed()).isFalse();
    }

    @Test
    void testTitleDefaultsToId() {
        // A button always has a name for screen readers
        assertThat(new VisButtonSpec("zoom", null, "search", null).getTitle()).isEqualTo("zoom");
        assertThat(new VisButtonSpec("zoom", " ", "search", null).getTitle()).isEqualTo("zoom");
    }

    @Test
    void testIdRequired() {
        assertThatThrownBy(() -> new VisButtonSpec(null, "Zoom", "search", null))
                .isInstanceOf(NullPointerException.class);
    }
}
