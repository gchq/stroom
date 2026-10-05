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

class TestZoomLevels {

    @Test
    void testZoomIn() {
        // The same steps as React Storybook
        int zoom = ZoomLevels.DEFAULT;
        final StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            zoom = ZoomLevels.zoomIn(zoom);
            steps.append(zoom).append(' ');
        }
        assertThat(steps.toString().trim()).isEqualTo("110 125 150 200 300 400 800 800");
    }

    @Test
    void testZoomOut() {
        int zoom = ZoomLevels.DEFAULT;
        final StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            zoom = ZoomLevels.zoomOut(zoom);
            steps.append(zoom).append(' ');
        }
        assertThat(steps.toString().trim()).isEqualTo("90 75 50 25 25");
    }

    @Test
    void testZoomFromNonLevel() {
        assertThat(ZoomLevels.zoomIn(333)).isEqualTo(400);
        assertThat(ZoomLevels.zoomOut(333)).isEqualTo(300);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "150,150",
            "150%,150",
            " 75 % ,75",
            "10,25",
            "5000,800",
            "99999999,800",
            "0,25",
            "800%,800",
            "0100,100",
    })
    void testParse(final String text, final int expected) {
        assertThat(ZoomLevels.parse(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "1.5", "-50", "%", " ", "%%", "50%%x", "1 0", "+50", "١٢"})
    void testParse_invalid(final String text) {
        assertThat(ZoomLevels.parse(text)).isNull();
    }
}
