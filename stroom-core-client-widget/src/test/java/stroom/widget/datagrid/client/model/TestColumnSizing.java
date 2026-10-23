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

package stroom.widget.datagrid.client.model;

import stroom.data.grid.client.ColSettings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestColumnSizing {

    @Test
    void testFromColSettings_fixed() {
        final ColumnSizing sizing = ColumnSizing.fromColSettings(new ColSettings(false, false), 120);

        assertThat(sizing).isEqualTo(ColumnSizing.fixed(120));
    }

    @Test
    void testFromColSettings_resizable() {
        final ColumnSizing sizing = ColumnSizing.fromColSettings(new ColSettings(true, true), 120);

        assertThat(sizing).isEqualTo(ColumnSizing.resizable(120));
    }

    @Test
    void testFromColSettings_resizableButNotMovable() {
        final ColumnSizing sizing = ColumnSizing.fromColSettings(new ColSettings(true, false), 80);

        assertThat(sizing.getType()).isEqualTo(ColumnWidthType.FIXED);
        assertThat(sizing.getWidth()).isEqualTo(80);
        assertThat(sizing.isResizable()).isTrue();
        assertThat(sizing.isMovable()).isFalse();
    }

    @Test
    void testFromColSettings_fill() {
        final ColumnSizing sizing = ColumnSizing.fromColSettings(
                new ColSettings(true, true, true, 50, 300), 150);

        // The width given is the floor; the settings' minimum width is ignored, as MyDataGrid does
        assertThat(sizing).isEqualTo(ColumnSizing.fill(50, 150));
    }

    @Test
    void testFromColSettings_fillWeightRounded() {
        final ColumnSizing sizing = ColumnSizing.fromColSettings(
                new ColSettings(true, true, true, 33.6, null), 100);

        assertThat(sizing.getFillWeight()).isEqualTo(34);
    }

    @Test
    void testFromColSettings_negativeWidth() {
        final ColumnSizing sizing = ColumnSizing.fromColSettings(new ColSettings(false, false), -5);

        assertThat(sizing.getWidth()).isZero();
    }
}
