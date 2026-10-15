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

package stroom.gwt.workbench.framework.client.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolPresets {

    @Test
    void testViewportPresets() {
        // Storybook's default viewports
        assertThat(ViewportPreset.fromId("mobile1").getLabel()).isEqualTo("Small mobile");
        assertThat(ViewportPreset.fromId("mobile1").getWidth()).isEqualTo(320);
        assertThat(ViewportPreset.fromId("mobile1").getHeight()).isEqualTo(568);
        assertThat(ViewportPreset.fromId("mobile2").getWidth()).isEqualTo(414);
        assertThat(ViewportPreset.fromId("tablet").getHeight()).isEqualTo(1112);
        assertThat(ViewportPreset.fromId("desktop").getWidth()).isEqualTo(1280);
        assertThat(ViewportPreset.fromId("unknown")).isNull();
    }

    @Test
    void testVisionFilters() {
        assertThat(VisionFilter.fromId("blurred").getCssFilter()).isEqualTo("blur(2px)");
        assertThat(VisionFilter.fromId("blurred").getDescription()).isEqualTo("22.9% of users");
        assertThat(VisionFilter.fromId("protanopia").getCssFilter())
                .isEqualTo("url(\"#workbench-a11y-vision-protanopia\")");
        assertThat(VisionFilter.fromId("grayscale").getDescription()).isNull();
        // Storybook's id and the CSS function keep their spelling, the label is British English
        assertThat(VisionFilter.fromId("grayscale")).isEqualTo(VisionFilter.GREYSCALE);
        assertThat(VisionFilter.GREYSCALE.getLabel()).isEqualTo("Greyscale");
        assertThat(VisionFilter.GREYSCALE.getCssFilter()).isEqualTo("grayscale(100%)");
        assertThat(VisionFilter.fromId("unknown")).isNull();
        assertThat(VisionFilter.fromId(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "320,320",
            "320px,320",
            " 320 px ,320",
            "0,0",
            "00040,40",
            "99999,99999",
    })
    void testParseSize(final String text, final int expected) {
        assertThat(ViewportPreset.parseSize(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"px", "abc", "-5", "1.5", "320pxpx", "3px2", "123456", "320 em", "١٢٣"})
    void testParseSize_invalid(final String text) {
        assertThat(ViewportPreset.parseSize(text)).isNull();
    }
}
